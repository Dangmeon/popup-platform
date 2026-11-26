package com.popq.platform.reservation.service;

import com.popq.platform.auth.entity.User;
import com.popq.platform.auth.repository.UserRepository;
import com.popq.platform.common.redis.RedisExpirationKeyService;
import com.popq.platform.exception.AuthErrorCode;
import com.popq.platform.exception.AuthException;
import com.popq.platform.popup.entity.PopupStore;
import com.popq.platform.reservation.dto.ReservationCreateRequest;
import com.popq.platform.reservation.dto.ReservationResponse;
import com.popq.platform.reservation.entity.Reservation;
import com.popq.platform.reservation.entity.ReservationStatus;
import com.popq.platform.reservation.event.ReservationHoldCreatedEvent;
import com.popq.platform.reservation.exception.ReservationErrorCode;
import com.popq.platform.reservation.exception.ReservationException;
import com.popq.platform.reservation.redis.ReservationExpirationService;
import com.popq.platform.reservation.repository.ReservationRepository;
import com.popq.platform.timeslot.dto.TimeSlotChangedEvent;
import com.popq.platform.timeslot.entity.TimeSlot;
import com.popq.platform.timeslot.repository.TimeSlotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private TimeSlotRepository timeSlotRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RedisExpirationKeyService redisExpirationKeyService;

    @Mock
    private ReservationExpirationService reservationExpirationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ReservationService reservationService;

    @ParameterizedTest // 여러 입력값으로 동일한 테스트를 반복 실행하는 어노테이션
    @ValueSource(ints = {-1, 0, 4}) // peopleCount에 -1, 0, 4를 각각 전달하여 총 3번 테스트 실행
    @DisplayName("예약 인원이 1~3명 범위를 벗어나면 예약에 실패한다") // 테스트 실행 결과에 표시할 이름
    void createReservation_invalidPeopleCount(int peopleCount) {

        // Given : 테스트에 필요한 조건 및 데이터 준비
        // 테스트를 요청하는 사용자 아이디 설정
        String username = "testUser";

        // 예약 회차 ID를 1로 설정하고,
        // 예약 인원은 @ValueSource에서 전달받은 값으로 설정
        ReservationCreateRequest request =
                new ReservationCreateRequest(1L, peopleCount);

        // When : 테스트할 비즈니스 로직 실행
        // createReservation()을 실행했을 때 ReservationException이 발생하는지 검증
        // 예외가 발생하면 해당 예외 객체를 반환받음
        // assertThrows() :	예상한 예외가 발생하는지 검증
        ReservationException exception = assertThrows(
                ReservationException.class,
                () -> reservationService.createReservation(username, request)
        );

        // Then : 실제 실행 결과가 기대한 결과와 일치하는지 검증
        // 발생한 예외의 에러 코드가 INVALID_PEOPLE_COUNT인지 확인
        // assertEquals(기대값, 실제값)
        // assertEquals() :	기대값과 실제값이 일치하는지 검증
        assertEquals(
                ReservationErrorCode.INVALID_PEOPLE_COUNT,
                exception.getErrorCode()
        );

        // 잘못된 예약 인원으로 인해 예외가 발생했으므로
        // 이후의 DB 조회, Redis 처리, 이벤트 발행 등이
        // 실행되지 않았는지 검증
        // verifyNoInteractions() :	Mock 객체의 메서드가 한 번도 호출되지 않았는지 검증
        verifyNoInteractions(
                reservationRepository,
                timeSlotRepository,
                userRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("존재하지 않는 회원은 예약할 수 없다.")
    void createReservation_userNotFound(){

        String username = "testUser";

        ReservationCreateRequest request = new ReservationCreateRequest(1L, 2);

        // 회원 조회 시 회원이 존재하지 않는 상황을 설정
        // when() :	Mock 객체가 어떤 메서드를 호출받을지 설정
        // thenReturn() : 해당 메서드가 반환할 가짜 데이터 설정
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        AuthException exception = assertThrows(
                AuthException.class,
                () -> reservationService.createReservation(username, request)
        );

        assertEquals(AuthErrorCode.USER_NOT_FOUND, exception.getErrorCode());

        // 회원 조회가 1번 실행되었는지 확인
        // verify() : 실제로 해당 메서드가 호출되었는지 검증
        verify(userRepository).findByUsername(username);

        // 회원 조회 실패 이후의 로직이 실행되지 않았는지 확인
        // verifyNoInteractions() : 해당 Mock 객체가 한 번도 호출되지 않았는지 검증
        verifyNoInteractions(
                reservationRepository,
                timeSlotRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("존재하지 않는 회차는 예약할 수 없다.")
    void createReservation_timeSlotNotFound(){

        String username = "testUser";
        Long timeSlotId = 1L;

        ReservationCreateRequest request = new ReservationCreateRequest(timeSlotId, 2);

        // 회원 검증을 통과하도록 Mock 회원 생성
        User user = mock(User.class);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        // 존재하지 않는 회차 설정
        when(timeSlotRepository.findByIdForUpdate(timeSlotId)).thenReturn(Optional.empty());

        ReservationException exception = assertThrows(
                ReservationException.class,
                () -> reservationService.createReservation(username, request)
        );

        assertEquals(ReservationErrorCode.TIMESLOT_NOT_FOUND, exception.getErrorCode());

        // 회원 및 회차 조회가 각각 1회 실행되었는지 확인
        verify(userRepository).findByUsername(username);
        verify(timeSlotRepository).findByIdForUpdate(timeSlotId);

        verifyNoInteractions(
                reservationRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("운영 중이 아닌 팝업은 예약할 수 없다")
    void createReservation_popupNotOpen() {

        String username = "testUser";
        Long timeSlotId = 1L;

        ReservationCreateRequest request = new ReservationCreateRequest(timeSlotId, 2);

        // 회원 검증을 통과하도록 Mock 회원 생성
        User user = mock(User.class);
        TimeSlot timeSlot = mock(TimeSlot.class);
        PopupStore popupStore = mock(PopupStore.class);

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(timeSlotRepository.findByIdForUpdate(timeSlotId)).thenReturn(Optional.of(timeSlot));
        when(timeSlot.getPopupStore()).thenReturn(popupStore);
        when(popupStore.getOpStatus()).thenReturn("CLOSED");

        ReservationException exception = assertThrows(
                ReservationException.class,
                () -> reservationService.createReservation(username, request)
        );

        assertEquals(ReservationErrorCode.POPUP_NOT_OPEN, exception.getErrorCode());

        verify(userRepository).findByUsername(username);
        verify(timeSlotRepository).findByIdForUpdate(timeSlotId);
        verify(timeSlot).getPopupStore();
        verify(popupStore).getOpStatus();
        verify(timeSlot, never()).getSlotStatus();

        verifyNoInteractions(
                reservationRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("OPEN 상태가 아닌 회차는 예약할 수 없다")
    void createReservation_timeSlotNotOpen() {

        String username = "testUser";
        Long timeSlotId = 1L;

        ReservationCreateRequest request = new ReservationCreateRequest(timeSlotId, 2);

        // 회원 검증을 통과하도록 Mock 회원 생성
        User user = mock(User.class);
        TimeSlot timeSlot = mock(TimeSlot.class);
        PopupStore popupStore = mock(PopupStore.class);

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(timeSlotRepository.findByIdForUpdate(timeSlotId)).thenReturn(Optional.of(timeSlot));
        when(timeSlot.getPopupStore()).thenReturn(popupStore);
        when(popupStore.getOpStatus()).thenReturn("OPEN");
        when(timeSlot.getSlotStatus()).thenReturn("CLOSED");

        ReservationException exception = assertThrows(
                ReservationException.class,
                () -> reservationService.createReservation(username, request)
        );

        assertEquals(ReservationErrorCode.TIMESLOT_NOT_OPEN, exception.getErrorCode());

        verify(userRepository).findByUsername(username);
        verify(timeSlotRepository).findByIdForUpdate(timeSlotId);
        verify(timeSlot).getPopupStore();
        verify(popupStore).getOpStatus();
        verify(timeSlot).getSlotStatus();

        verifyNoInteractions(
                reservationRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1})
    @DisplayName("예약 가능 기간이 아니면 예약할 수 없다")
    void createReservation_invalidPeriod(int dayOffset) {

        String username = "testUser";
        Long timeSlotId = 1L;

        ReservationCreateRequest request = new ReservationCreateRequest(timeSlotId, 2);

        // 회원 검증을 통과하도록 Mock 회원 생성
        User user = mock(User.class);
        TimeSlot timeSlot = mock(TimeSlot.class);
        PopupStore popupStore = mock(PopupStore.class);

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(timeSlotRepository.findByIdForUpdate(timeSlotId)).thenReturn(Optional.of(timeSlot));

        when(timeSlot.getPopupStore()).thenReturn(popupStore);
        when(popupStore.getOpStatus()).thenReturn("OPEN");
        when(timeSlot.getSlotStatus()).thenReturn("OPEN");

        LocalDate slotDate;

        if(dayOffset == 1){
            slotDate = LocalDate.now().plusDays(
                    TimeSlot.RESERV_OPEN_DAYS_BEFORE + 1L
            );
        }else{
            slotDate = LocalDate.now().plusDays(dayOffset);
        }

        when(timeSlot.getSlotDate()).thenReturn(slotDate);

        ReservationException exception = assertThrows(
                ReservationException.class,
                () -> reservationService.createReservation(username, request)
        );

        assertEquals(ReservationErrorCode.RESERVATION_NOT_AVAILABLE, exception.getErrorCode());

        verify(userRepository).findByUsername(username);
        verify(timeSlotRepository).findByIdForUpdate(timeSlotId);
        verify(timeSlot).getPopupStore();
        verify(popupStore).getOpStatus();
        verify(timeSlot).getSlotStatus();

        verifyNoInteractions(
                reservationRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("동일 회원은 동일 회차에 중복 예약할 수 없다")
    void createReservation_duplicateReservation() {

        String username = "testUser";
        Long userId = 1L;
        Long timeSlotId = 1L;

        ReservationCreateRequest request = new ReservationCreateRequest(timeSlotId, 2);

        // 회원 검증을 통과하도록 Mock 회원 생성
        User user = mock(User.class);
        TimeSlot timeSlot = mock(TimeSlot.class);
        PopupStore popupStore = mock(PopupStore.class);

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(timeSlotRepository.findByIdForUpdate(timeSlotId)).thenReturn(Optional.of(timeSlot));

        when(timeSlot.getPopupStore()).thenReturn(popupStore);
        when(popupStore.getOpStatus()).thenReturn("OPEN");
        when(timeSlot.getSlotStatus()).thenReturn("OPEN");
        when(timeSlot.getSlotDate()).thenReturn(LocalDate.now().plusDays(1));
        when(user.getId()).thenReturn(userId);
        when(timeSlot.getTimeSlotId()).thenReturn(timeSlotId);

        when(reservationRepository.existsActiveReservation(
                userId,
                timeSlotId,
                List.of(
                        ReservationStatus.HOLD,
                        ReservationStatus.CONFIRMED,
                        ReservationStatus.USED
                )
        )).thenReturn(true);

        ReservationException exception = assertThrows(
                ReservationException.class,
                () -> reservationService.createReservation(username, request)
        );

        assertEquals(ReservationErrorCode.DUPLICATE_RESERVATION, exception.getErrorCode());

        verify(userRepository).findByUsername(username);
        verify(timeSlotRepository).findByIdForUpdate(timeSlotId);
        verify(timeSlot).getPopupStore();
        verify(popupStore).getOpStatus();
        verify(timeSlot).getSlotStatus();
        verify(reservationRepository).existsActiveReservation(
                userId,
                timeSlotId,
                List.of(
                        ReservationStatus.HOLD,
                        ReservationStatus.CONFIRMED,
                        ReservationStatus.USED
                )
        );

        // 정원 검증이 실행되지 않았는지 확인
        verify(timeSlot, never()).getReservedCount();
        verify(timeSlot, never()).getCapacity();

        // 예약 인원이 증가하지 않았는지 확인
        verify(timeSlot, never()).increaseReservedCount(anyInt());

        // 예약 데이터가 저장되지 않았는지 확인
        verify(reservationRepository, never()).save(any());

        verifyNoInteractions(
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {9, 10})
    @DisplayName("잔여 정원을 초과하면 예약할 수 없다")
    void createReservation_capacityExceeded(int reservedCount) {

        String username = "testUser";
        Long userId = 1L;
        Long timeSlotId = 1L;

        ReservationCreateRequest request = new ReservationCreateRequest(timeSlotId, 2);

        // 회원 검증을 통과하도록 Mock 회원 생성
        User user = mock(User.class);
        TimeSlot timeSlot = mock(TimeSlot.class);
        PopupStore popupStore = mock(PopupStore.class);

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(timeSlotRepository.findByIdForUpdate(timeSlotId)).thenReturn(Optional.of(timeSlot));

        when(timeSlot.getPopupStore()).thenReturn(popupStore);
        when(popupStore.getOpStatus()).thenReturn("OPEN");
        when(timeSlot.getSlotStatus()).thenReturn("OPEN");
        when(timeSlot.getSlotDate()).thenReturn(LocalDate.now().plusDays(1));
        when(user.getId()).thenReturn(userId);
        when(timeSlot.getTimeSlotId()).thenReturn(timeSlotId);

        when(reservationRepository.existsActiveReservation(
                userId,
                timeSlotId,
                List.of(
                        ReservationStatus.HOLD,
                        ReservationStatus.CONFIRMED,
                        ReservationStatus.USED
                )
        )).thenReturn(false);

        when(timeSlot.getReservedCount()).thenReturn(reservedCount);
        when(timeSlot.getCapacity()).thenReturn(10);

        ReservationException exception = assertThrows(
                ReservationException.class,
                () -> reservationService.createReservation(username, request)
        );

        assertEquals(ReservationErrorCode.CAPACITY_EXCEEDED, exception.getErrorCode());

        verify(userRepository).findByUsername(username);
        verify(timeSlotRepository).findByIdForUpdate(timeSlotId);
        verify(timeSlot).getPopupStore();
        verify(popupStore).getOpStatus();
        verify(timeSlot).getSlotStatus();
        verify(reservationRepository).existsActiveReservation(
                userId,
                timeSlotId,
                List.of(
                        ReservationStatus.HOLD,
                        ReservationStatus.CONFIRMED,
                        ReservationStatus.USED
                )
        );

        // 정원 검증이 실행되었는지 확인
        verify(timeSlot).getReservedCount();
        verify(timeSlot).getCapacity();

        // 예약 인원이 증가하지 않았는지 확인
        verify(timeSlot, never()).increaseReservedCount(anyInt());

        // 예약 데이터가 저장되지 않았는지 확인
        verify(reservationRepository, never()).save(any());

        verifyNoInteractions(
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("정상 예약 생성 시 HOLD 상태로 저장되고 예약 인원이 증가한다")
    void createReservation_success() {

        String username = "testUser";
        Long userId = 1L;
        Long timeSlotId = 1L;
        int peopleCount = 2;

        ReservationCreateRequest request = new ReservationCreateRequest(timeSlotId, peopleCount);

        User user = mock(User.class);
        TimeSlot timeSlot = mock(TimeSlot.class);
        PopupStore popupStore = mock(PopupStore.class);

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(timeSlotRepository.findByIdForUpdate(timeSlotId)).thenReturn(Optional.of(timeSlot));
        when(timeSlot.getPopupStore()).thenReturn(popupStore);
        when(popupStore.getOpStatus()).thenReturn("OPEN");
        when(timeSlot.getSlotStatus()).thenReturn("OPEN");
        when(timeSlot.getSlotDate()).thenReturn(LocalDate.now().plusDays(1));
        when(user.getId()).thenReturn(userId);
        when(timeSlot.getTimeSlotId()).thenReturn(timeSlotId);
        when(reservationRepository.existsActiveReservation(
                userId,
                timeSlotId,
                List.of(
                        ReservationStatus.HOLD,
                        ReservationStatus.CONFIRMED,
                        ReservationStatus.USED
                )
        )).thenReturn(false);
        when(timeSlot.getReservedCount()).thenReturn(8);
        when(timeSlot.getCapacity()).thenReturn(10);

        // thenAnswer() : 실제 DB에 저장하지 않고 save()에 전달된 예약 객체를 그대로 반환하는 방식
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArguments()[0]);

        ReservationResponse response = reservationService.createReservation(username, request);
        // 정상 응답 반환 확인
        assertNotNull(response);

        // 저장된 예약 객체를 가져오기 위한 Captor 생성
        // ArgumentCaptor : 서비스 내부에서 생성한 Reservation 객체를 가져와서 검증할 수 있게 해주는 기능
        ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);

        // 예약 데이터 저장 확인
        verify(reservationRepository).save(reservationCaptor.capture());

        Reservation savedReservation = reservationCaptor.getValue();

        // 예약 상태 검증
        assertEquals(ReservationStatus.HOLD, savedReservation.getReservationStatus());

        assertEquals(peopleCount, savedReservation.getPeopleCount());

        // 10분 만료시간 설정 검증
        assertNotNull(savedReservation.getExpiresAt());

        assertEquals(
                savedReservation.getReservedAt().plusMinutes(10),
                savedReservation.getExpiresAt());

        // 예약 인원 증가 메서드 호출 확인
        verify(timeSlot).increaseReservedCount(peopleCount);

        // 회차 변경 이벤트 발행 확인
        verify(eventPublisher).publishEvent(any(TimeSlotChangedEvent.class));

        // HOLD 예약 생성 이벤트 발행 확인
        verify(eventPublisher).publishEvent(any(ReservationHoldCreatedEvent.class));

        // Redis 직접 호출 및 만료 서비스 호출이 없는지 확인
        verifyNoInteractions(
                redisExpirationKeyService,
                reservationExpirationService
        );
    }
}

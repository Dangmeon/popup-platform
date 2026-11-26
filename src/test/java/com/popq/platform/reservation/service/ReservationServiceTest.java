package com.popq.platform.reservation.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.popq.platform.auth.entity.User;
import com.popq.platform.auth.repository.UserRepository;
import com.popq.platform.common.redis.RedisExpirationKeyService;
import com.popq.platform.exception.AuthErrorCode;
import com.popq.platform.exception.AuthException;
import com.popq.platform.popup.entity.PopupStore;
import com.popq.platform.reservation.dto.ReservationCreateRequest;
import com.popq.platform.reservation.dto.ReservationDetailResponse;
import com.popq.platform.reservation.dto.ReservationResponse;
import com.popq.platform.reservation.dto.ReservationUpdateRequest;
import com.popq.platform.reservation.entity.Reservation;
import com.popq.platform.reservation.entity.ReservationStatus;
import com.popq.platform.reservation.event.ReservationHoldCreatedEvent;
import com.popq.platform.reservation.exception.ReservationErrorCode;
import com.popq.platform.reservation.exception.ReservationException;
import com.popq.platform.reservation.redis.ReservationExpirationService;
import com.popq.platform.reservation.repository.ReservationRepository;
import com.popq.platform.timeslot.dto.TimeSlotChangedEvent;
import com.popq.platform.timeslot.entity.TimeSlot;
import com.popq.platform.timeslot.exception.TimeSlotErrorCode;
import com.popq.platform.timeslot.exception.TimeSlotException;
import com.popq.platform.timeslot.repository.TimeSlotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    // ==================== 예약 상세 조회 ====================

    @Test
    @DisplayName("존재하지 않는 예약은 조회할 수 없다")
    void getReservation_reservationNotFound() {

        String username = "testUser";
        Long reservationId = 1L;

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

        ReservationException exception = assertThrows(
                ReservationException.class,
                () -> reservationService.getReservation(username, reservationId)
        );

        assertEquals(ReservationErrorCode.RESERVATION_NOT_FOUND, exception.getErrorCode());

        verify(reservationRepository).findById(reservationId);

        verifyNoInteractions(
                userRepository,
                timeSlotRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("존재하지 않는 회원은 예약을 조회할 수 없다")
    void getReservation_userNotFound() {

        String username = "testUser";
        Long reservationId = 1L;

        Reservation reservation = mock(Reservation.class);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        AuthException exception = assertThrows(
                AuthException.class,
                () -> reservationService.getReservation(username, reservationId)
        );

        assertEquals(AuthErrorCode.USER_NOT_FOUND, exception.getErrorCode());

        verify(userRepository).findByUsername(username);
        verify(reservationRepository).findById(reservationId);

        // 예약 소유자 검증이 실행되지 않았는지 확인
        verify(reservation, never()).getUser();

        verifyNoInteractions(
                timeSlotRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("다른 회원의 예약은 조회할 수 없다")
    void getReservation_accessDenied() {

        String username = "testUser";
        Long reservationId = 1L;
        Long userId = 1L;
        Long ownerId = 2L;

        Reservation reservation = mock(Reservation.class);
        User user = mock(User.class);
        User owner = mock(User.class);

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        when(user.getId()).thenReturn(userId);

        when(reservation.getUser()).thenReturn(owner);
        when(owner.getId()).thenReturn(ownerId);

        AuthException exception = assertThrows(
                AuthException.class,
                () -> reservationService.getReservation(username, reservationId)
        );

        assertEquals(AuthErrorCode.ACCESS_DENIED, exception.getErrorCode());

        verify(userRepository).findByUsername(username);
        verify(reservationRepository).findById(reservationId);

        // 예약 소유자 검증이 실행되지 않았는지 확인
        verify(reservation).getUser();
        verify(user).getId();
        verify(owner).getId();

        verifyNoInteractions(
                timeSlotRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("본인의 예약 상세 정보를 정상적으로 조회한다")
    void getReservation_success() {

        String username = "testUser";
        Long reservationId = 1L;
        Long userId = 1L;
        Long timeSlotId = 1L;
        Long popId = 1L;

        LocalDate slotDate = LocalDate.now().plusDays(1);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);

        Reservation reservation = mock(Reservation.class);
        User user = mock(User.class);
        TimeSlot timeSlot = mock(TimeSlot.class);
        PopupStore popupStore = mock(PopupStore.class);

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);
        when(reservation.getUser()).thenReturn(user);

        // 예약 정보 설정
        when(reservation.getReservationId()).thenReturn(reservationId);
        when(reservation.getPeopleCount()).thenReturn(2);
        when(reservation.getReservationStatus())
                .thenReturn(ReservationStatus.HOLD);
        when(reservation.getExpiresAt()).thenReturn(expiresAt);
        when(reservation.getQrToken()).thenReturn(null);

        // 회차 정보 설정
        when(reservation.getTimeSlot()).thenReturn(timeSlot);
        when(timeSlot.getTimeSlotId()).thenReturn(timeSlotId);
        when(timeSlot.getSlotDate()).thenReturn(slotDate);
        when(timeSlot.getStartTime()).thenReturn("14:00");
        when(timeSlot.getEndTime()).thenReturn("15:00");

        // 팝업 정보 설정
        when(timeSlot.getPopupStore()).thenReturn(popupStore);
        when(popupStore.getPopId()).thenReturn(popId);
        when(popupStore.getTitle()).thenReturn("테스트 팝업");
        when(popupStore.getStoreImg()).thenReturn("popup.jpg");

        ReservationDetailResponse response =
                reservationService.getReservation(username, reservationId);

        assertNotNull(response);

        // 예약 정보 검증
        assertEquals(reservationId, response.reservationId());
        assertEquals(2, response.peopleCount());
        assertEquals(ReservationStatus.HOLD, response.reservationStatus());
        assertEquals(expiresAt, response.expiresAt());
        assertNull(response.qrToken());

        // 회차 정보 검증
        assertEquals(timeSlotId, response.timeSlotId());
        assertEquals(slotDate, response.slotDate());
        assertEquals("14:00", response.startTime());
        assertEquals("15:00", response.endTime());

        // 팝업 정보 검증
        assertEquals(popId, response.popId());
        assertEquals("테스트 팝업", response.popupName());
        assertEquals("popup.jpg", response.popupImage());
        verify(userRepository).findByUsername(username);
        verify(reservationRepository).findById(reservationId);

        // Repository 호출 검증
        verify(reservationRepository).findById(reservationId);
        verify(userRepository).findByUsername(username);

        // 예약 소유자 검증
        verify(reservation).getUser();
        verify(user, times(2)).getId();

        verifyNoInteractions(
                timeSlotRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    // ==================== 예약 확정 ====================

    @Test
    @DisplayName("존재하지 않는 예약은 확정할 수 없다")
    void confirmReservation_reservationNotFound() {

        String username = "testUser";
        Long reservationId = 1L;

        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.empty());

        ReservationException exception = assertThrows(
                ReservationException.class,
                () -> reservationService.confirmReservation(username, reservationId)
        );

        assertEquals(ReservationErrorCode.RESERVATION_NOT_FOUND, exception.getErrorCode());

        verify(reservationRepository).findByIdForUpdate(reservationId);

        verifyNoInteractions(
                userRepository,
                timeSlotRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("존재하지 않는 회원은 예약을 확정할 수 없다")
    void confirmReservation_userNotFound() {

        String username = "testUser";
        Long reservationId = 1L;

        Reservation reservation = mock(Reservation.class);
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        AuthException exception = assertThrows(
                AuthException.class,
                () -> reservationService.confirmReservation(username, reservationId)
        );

        assertEquals(AuthErrorCode.USER_NOT_FOUND, exception.getErrorCode());

        verify(reservationRepository).findByIdForUpdate(reservationId);
        verify(userRepository).findByUsername(username);

        verifyNoInteractions(
                timeSlotRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("다른 회원의 예약은 확정할 수 없다")
    void confirmReservation_accessDenied() {

        String username = "testUser";
        Long reservationId = 1L;
        Long userId = 1L;
        Long ownerId = 2L;

        Reservation reservation = mock(Reservation.class);
        User user = mock(User.class);
        User owner = mock(User.class);

        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        when(user.getId()).thenReturn(userId);

        when(reservation.getUser()).thenReturn(owner);
        when(owner.getId()).thenReturn(ownerId);

        AuthException exception = assertThrows(
                AuthException.class,
                () -> reservationService.confirmReservation(username, reservationId)
        );

        assertEquals(AuthErrorCode.ACCESS_DENIED, exception.getErrorCode());

        verify(userRepository).findByUsername(username);
        verify(reservationRepository).findByIdForUpdate(reservationId);

        // 예약 소유자 검증이 실행되지 않았는지 확인
        verify(reservation).getUser();
        verify(user).getId();
        verify(owner).getId();

        // 예약 상태 및 만료 여부 검증 미실행 확인
        verify(reservation, never()).getReservationStatus();
        verify(reservation, never()).getExpiresAt();

        // 예약 확정 처리가 실행되지 않았는지 확인
        verify(reservation, never()).confirm(anyString());

        verifyNoInteractions(
                timeSlotRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = ReservationStatus.class,
            names = {"HOLD"},
            mode = EnumSource.Mode.EXCLUDE
    )
    @DisplayName("HOLD 상태가 아닌 예약은 확정할 수 없다")
    void confirmReservation_invalidStatus(ReservationStatus status) {

        String username = "testUser";
        Long reservationId = 1L;
        Long userId = 1L;

        Reservation reservation = mock(Reservation.class);
        User user = mock(User.class);
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        when(user.getId()).thenReturn(userId);
        when(reservation.getUser()).thenReturn(user);

        when(reservation.getReservationStatus()).thenReturn(status);

        ReservationException exception = assertThrows(
                ReservationException.class,
                () -> reservationService.confirmReservation(username, reservationId)
        );

        assertEquals(ReservationErrorCode.INVALID_RESERVATION_STATUS, exception.getErrorCode());

        // 예약 및 회원 조회 확인
        verify(reservationRepository).findByIdForUpdate(reservationId);
        verify(userRepository).findByUsername(username);

        // 예약 소유자 검증 확인
        verify(reservation).getUser();
        verify(user, times(2)).getId();

        verify(reservation).getReservationStatus();

        // 예약 만료 여부 검증 미실행 확인
        verify(reservation, never()).getExpiresAt();

        // 예약 확정 처리 미실행 확인
        verify(reservation, never()).confirm(anyString());

        verifyNoInteractions(
                timeSlotRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("만료된 예약은 확정하지 않고 만료 처리한다")
    void confirmReservation_expired() {

        String username = "testUser";
        Long reservationId = 1L;
        Long userId = 1L;
        Long timeSlotId = 1L;

        LocalDateTime expiresAt = LocalDateTime.now().minusMinutes(1);

        Reservation reservation = mock(Reservation.class);
        User user = mock(User.class);
        TimeSlot timeSlot = mock(TimeSlot.class);

        // 예약 및 회원 조회 성공
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        // 예약 소유자 검증 통과
        when(user.getId()).thenReturn(userId);
        when(reservation.getUser()).thenReturn(user);

        // 예약 상태 및 만료시간 설정
        when(reservation.getReservationStatus()).thenReturn(ReservationStatus.HOLD);

        when(reservation.getExpiresAt()).thenReturn(expiresAt);

        // 응답 DTO 변환에 필요한 데이터 설정
        when(reservation.getReservationId()).thenReturn(reservationId);
        when(reservation.getTimeSlot()).thenReturn(timeSlot);
        when(timeSlot.getTimeSlotId()).thenReturn(timeSlotId);
        when(reservation.getPeopleCount()).thenReturn(2);
        when(reservation.getQrToken()).thenReturn(null);

        // 만료 서비스가 예약 상태를 변경하는 상황을 모의 처리
        doAnswer(invocation -> {
            when(reservation.getReservationStatus()).thenReturn(ReservationStatus.EXPIRED);
            return null;
        }).when(reservationExpirationService).expireReservation(reservationId);

        ReservationResponse response = reservationService.confirmReservation(username, reservationId);

        assertNotNull(response);

        // 응답 DTO 검증
        assertEquals(reservationId, response.reservationId());
        assertEquals(timeSlotId, response.timeSlotId());
        assertEquals(2, response.peopleCount());
        assertEquals(ReservationStatus.EXPIRED, response.reservationStatus());
        assertEquals(expiresAt, response.expiresAt());
        assertNull(response.qrToken());

        // 예약 및 회원 조회 확인
        verify(reservationRepository).findByIdForUpdate(reservationId);
        verify(userRepository).findByUsername(username);

        // 예약 소유자 검증 확인
        verify(reservation).getUser();
        verify(user, times(2)).getId();

        // 예약 상태 및 만료시간 조회 확인
        verify(reservation, atLeastOnce()).getReservationStatus();
        verify(reservation, atLeastOnce()).getExpiresAt();

        // 예약 만료 서비스 호출 확인
        verify(reservationExpirationService).expireReservation(reservationId);

        // 예약 확정 처리 미실행 확인
        verify(reservation, never()).confirm(anyString());

        // 불필요한 직접 호출 미실행 확인
        verifyNoInteractions(
                timeSlotRepository,
                redisExpirationKeyService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("정상 예약 확정 시 CONFIRMED 상태로 변경하고 QR 토큰을 발급한다")
    void confirmReservation_success() {

        String username = "testUser";
        Long reservationId = 1L;
        Long userId = 1L;
        Long timeSlotId = 1L;
        int peopleCount = 2;

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);

        Reservation reservation = mock(Reservation.class);
        User user = mock(User.class);
        TimeSlot timeSlot = mock(TimeSlot.class);

        // 예약 및 회원 조회 성공
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        // 예약 소유자 검증 통과
        when(user.getId()).thenReturn(userId);
        when(reservation.getUser()).thenReturn(user);

        // 예약 상태 및 만료시간 검증 통과
        when(reservation.getReservationStatus()).thenReturn(ReservationStatus.HOLD);

        when(reservation.getExpiresAt()).thenReturn(expiresAt);

        // 응답 DTO 변환에 필요한 데이터 설정
        when(reservation.getReservationId()).thenReturn(reservationId);
        when(reservation.getTimeSlot()).thenReturn(timeSlot);
        when(timeSlot.getTimeSlotId()).thenReturn(timeSlotId);
        when(reservation.getPeopleCount()).thenReturn(peopleCount);

        // 예약 확정에 따른 상태 변경 및 QR 토큰 저장 모의 처리
        doAnswer(invocation -> {

            String token = invocation.getArgument(0);

            when(reservation.getReservationStatus()).thenReturn(ReservationStatus.CONFIRMED);

            when(reservation.getQrToken()).thenReturn(token);

            return null;

        }).when(reservation).confirm(anyString());

        ReservationResponse response = reservationService.confirmReservation(username, reservationId);

        assertNotNull(response);

        // confirm()에 전달된 QR 토큰 캡처
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);

        verify(reservation).confirm(tokenCaptor.capture());

        String qrToken = tokenCaptor.getValue();

        // QR 토큰 검증
        assertNotNull(qrToken);
        assertFalse(qrToken.isBlank());

        // UUID 형식인지 확인
        assertDoesNotThrow(() -> {UUID.fromString(qrToken);});

        // 응답 DTO 검증
        assertEquals(reservationId, response.reservationId());
        assertEquals(timeSlotId, response.timeSlotId());
        assertEquals(peopleCount, response.peopleCount());
        assertEquals(
                ReservationStatus.CONFIRMED,
                response.reservationStatus()
        );
        assertEquals(expiresAt, response.expiresAt());
        assertEquals(qrToken, response.qrToken());

        // 예약 및 회원 조회 확인
        verify(reservationRepository).findByIdForUpdate(reservationId);
        verify(userRepository).findByUsername(username);

        // 예약 소유자 검증 확인
        verify(reservation).getUser();
        verify(user, times(2)).getId();

        // 예약 상태 및 만료시간 조회 확인
        verify(reservation, atLeastOnce()).getReservationStatus();
        verify(reservation, atLeastOnce()).getExpiresAt();

        // Redis TTL Key 삭제 확인
        verify(redisExpirationKeyService).delete("reservation:hold:" + reservationId);

        // 별도의 예약 저장 호출이 발생하지 않는지 확인
        verify(reservationRepository, never()).save(any());

        // 불필요한 후속 처리 미실행 확인
        verifyNoInteractions(
                timeSlotRepository,
                reservationExpirationService,
                eventPublisher
        );
    }

    // ==================== 예약 변경 ====================

    @Test
    @DisplayName("존재하지 않는 예약은 변경할 수 없다")
    void updateReservation_reservationNotFound() {

        String username = "testUser";
        Long reservationId = 1L;

        ReservationUpdateRequest request = new ReservationUpdateRequest(1L, 2);

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

        ReservationException exception = assertThrows(
                ReservationException.class,
                () -> reservationService.updateReservation(username, reservationId, request)
        );

        assertEquals(
                ReservationErrorCode.RESERVATION_NOT_FOUND,
                exception.getErrorCode()
        );

        verify(reservationRepository).findById(reservationId);

        verifyNoInteractions(
                userRepository,
                timeSlotRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("존재하지 않는 회원은 예약을 변경할 수 없다")
    void updateReservation_userNotFound() {

        String username = "testUser";
        Long reservationId = 1L;

        ReservationUpdateRequest request = new ReservationUpdateRequest(1L, 2);

        Reservation reservation = mock(Reservation.class);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        AuthException exception = assertThrows(
                AuthException.class,
                () -> reservationService.updateReservation(username, reservationId, request)
        );

        assertEquals(
                AuthErrorCode.USER_NOT_FOUND,
                exception.getErrorCode()
        );

        verify(reservationRepository).findById(reservationId);
        verify(userRepository).findByUsername(username);

        verify(reservation, never()).getUser();
        verify(reservation, never()).getReservationStatus();

        verifyNoInteractions(
                timeSlotRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("다른 회원의 예약은 변경할 수 없다")
    void updateReservation_accessDenied() {

        String username = "testUser";
        Long reservationId = 1L;
        Long userId = 1L;
        Long ownerId = 2L;

        ReservationUpdateRequest request = new ReservationUpdateRequest(1L, 2);

        Reservation reservation = mock(Reservation.class);
        User user = mock(User.class);
        User owner = mock(User.class);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        when(user.getId()).thenReturn(userId);
        when(reservation.getUser()).thenReturn(owner);
        when(owner.getId()).thenReturn(ownerId);

        AuthException exception = assertThrows(
                AuthException.class,
                () -> reservationService.updateReservation(username, reservationId, request)
        );

        assertEquals(
                AuthErrorCode.ACCESS_DENIED,
                exception.getErrorCode()
        );

        verify(reservationRepository).findById(reservationId);
        verify(userRepository).findByUsername(username);

        verify(reservation).getUser();
        verify(user).getId();
        verify(owner).getId();

        verify(reservation, never()).getReservationStatus();
        verify(reservation, never()).changeReservation(any(), anyInt());
        verify(reservation, never()).renewQrToken(anyString());

        verifyNoInteractions(
                timeSlotRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = ReservationStatus.class,
            names = {"CONFIRMED"},
            mode = EnumSource.Mode.EXCLUDE
    )
    @DisplayName("CONFIRMED 상태가 아닌 예약은 변경할 수 없다")
    void updateReservation_invalidStatus(ReservationStatus status) {

        String username = "testUser";
        Long reservationId = 1L;
        Long userId = 1L;

        ReservationUpdateRequest request = new ReservationUpdateRequest(1L, 2);

        Reservation reservation = mock(Reservation.class);
        User user = mock(User.class);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        when(user.getId()).thenReturn(userId);
        when(reservation.getUser()).thenReturn(user);

        when(reservation.getReservationStatus()).thenReturn(status);

        ReservationException exception = assertThrows(
                ReservationException.class,
                () -> reservationService.updateReservation(username, reservationId, request)
        );

        assertEquals(
                ReservationErrorCode.INVALID_RESERVATION_STATUS,
                exception.getErrorCode()
        );

        verify(reservationRepository).findById(reservationId);
        verify(userRepository).findByUsername(username);

        verify(reservation).getUser();
        verify(user, times(2)).getId();

        verify(reservation).getReservationStatus();

        verify(reservation, never()).changeReservation(any(), anyInt());
        verify(reservation, never()).renewQrToken(anyString());

        verifyNoInteractions(
                timeSlotRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("존재하지 않는 회차로 예약을 변경할 수 없다")
    void updateReservation_timeSlotNotFound() {

        String username = "testUser";
        Long reservationId = 1L;
        Long userId = 1L;
        Long oldTimeSlotId = 1L;
        Long newTimeSlotId = 2L;

        ReservationUpdateRequest request = new ReservationUpdateRequest(newTimeSlotId, 2);

        Reservation reservation = mock(Reservation.class);
        User user = mock(User.class);
        TimeSlot oldTimeSlot = mock(TimeSlot.class);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);
        when(reservation.getUser()).thenReturn(user);
        when(reservation.getReservationStatus()).thenReturn(ReservationStatus.CONFIRMED);
        when(reservation.getTimeSlot()).thenReturn(oldTimeSlot);
        when(oldTimeSlot.getTimeSlotId()).thenReturn(oldTimeSlotId);

        // 기존 회차 조회 성공
        when(timeSlotRepository.findByIdForUpdate(oldTimeSlotId)).thenReturn(Optional.of(oldTimeSlot));

        // 변경할 회차 조회 실패
        when(timeSlotRepository.findByIdForUpdate(newTimeSlotId)).thenReturn(Optional.empty());

        ReservationException exception = assertThrows(
                ReservationException.class,
                () -> reservationService.updateReservation(username, reservationId, request)
        );

        assertEquals(
                ReservationErrorCode.TIMESLOT_NOT_FOUND,
                exception.getErrorCode()
        );

        verify(reservationRepository).findById(reservationId);
        verify(userRepository).findByUsername(username);

        verify(timeSlotRepository).findByIdForUpdate(oldTimeSlotId);
        verify(timeSlotRepository).findByIdForUpdate(newTimeSlotId);

        verify(oldTimeSlot, never()).decreaseReservedCount(anyInt());
        verify(reservation, never()).changeReservation(any(), anyInt());
        verify(reservation, never()).renewQrToken(anyString());

        verifyNoInteractions(
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("OPEN 상태가 아닌 회차로 예약을 변경할 수 없다")
    void updateReservation_timeSlotNotOpen() {

        String username = "testUser";
        Long reservationId = 1L;
        Long userId = 1L;
        Long timeSlotId = 1L;

        ReservationUpdateRequest request = new ReservationUpdateRequest(timeSlotId, 2);

        Reservation reservation = mock(Reservation.class);
        User user = mock(User.class);
        TimeSlot timeSlot = mock(TimeSlot.class);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);
        when(reservation.getUser()).thenReturn(user);
        when(reservation.getReservationStatus()).thenReturn(ReservationStatus.CONFIRMED);
        when(reservation.getTimeSlot()).thenReturn(timeSlot);
        when(timeSlot.getTimeSlotId()).thenReturn(timeSlotId);
        when(timeSlotRepository.findByIdForUpdate(timeSlotId)).thenReturn(Optional.of(timeSlot));

        when(timeSlot.getSlotStatus()).thenReturn("CLOSED");

        ReservationException exception = assertThrows(
                ReservationException.class,
                () -> reservationService.updateReservation(username, reservationId, request)
        );

        assertEquals(
                ReservationErrorCode.TIMESLOT_NOT_OPEN,
                exception.getErrorCode()
        );

        verify(reservationRepository).findById(reservationId);
        verify(userRepository).findByUsername(username);
        verify(timeSlotRepository).findByIdForUpdate(timeSlotId);

        verify(timeSlot).getSlotStatus();

        verify(timeSlot, never()).getSlotDate();
        verify(timeSlot, never()).increaseReservedCount(anyInt());
        verify(timeSlot, never()).decreaseReservedCount(anyInt());

        verify(reservation, never()).changeReservation(any(), anyInt());
        verify(reservation, never()).renewQrToken(anyString());

        verifyNoInteractions(
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1})
    @DisplayName("예약 가능 기간이 아니면 예약을 변경할 수 없다")
    void updateReservation_invalidPeriod(int dayOffset) {

        String username = "testUser";
        Long reservationId = 1L;
        Long userId = 1L;
        Long timeSlotId = 1L;

        ReservationUpdateRequest request = new ReservationUpdateRequest(timeSlotId, 2);

        Reservation reservation = mock(Reservation.class);
        User user = mock(User.class);
        TimeSlot timeSlot = mock(TimeSlot.class);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);
        when(reservation.getUser()).thenReturn(user);
        when(reservation.getReservationStatus()).thenReturn(ReservationStatus.CONFIRMED);
        when(reservation.getTimeSlot()).thenReturn(timeSlot);
        when(timeSlot.getTimeSlotId()).thenReturn(timeSlotId);
        when(timeSlotRepository.findByIdForUpdate(timeSlotId)).thenReturn(Optional.of(timeSlot));

        when(timeSlot.getSlotStatus()).thenReturn("OPEN");

        LocalDate slotDate;

        if (dayOffset == 1) {
            slotDate = LocalDate.now().plusDays(TimeSlot.RESERV_OPEN_DAYS_BEFORE + 1L);
        } else {
            slotDate = LocalDate.now().plusDays(dayOffset);
        }

        when(timeSlot.getSlotDate()).thenReturn(slotDate);

        ReservationException exception = assertThrows(
                ReservationException.class,
                () -> reservationService.updateReservation(username, reservationId, request)
        );

        assertEquals(
                ReservationErrorCode.RESERVATION_NOT_AVAILABLE,
                exception.getErrorCode()
        );

        verify(reservationRepository).findById(reservationId);
        verify(userRepository).findByUsername(username);
        verify(timeSlotRepository).findByIdForUpdate(timeSlotId);

        verify(timeSlot).getSlotStatus();
        verify(timeSlot, atLeastOnce()).getSlotDate();

        verify(timeSlot, never()).increaseReservedCount(anyInt());
        verify(timeSlot, never()).decreaseReservedCount(anyInt());

        verify(reservation, never()).changeReservation(any(), anyInt());
        verify(reservation, never()).renewQrToken(anyString());

        verifyNoInteractions(
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 4})
    @DisplayName("유효하지 않은 인원으로 예약을 변경할 수 없다")
    void updateReservation_invalidPeopleCount(int peopleCount) {

        String username = "testUser";
        Long reservationId = 1L;
        Long userId = 1L;
        Long timeSlotId = 1L;

        ReservationUpdateRequest request = new ReservationUpdateRequest(timeSlotId, peopleCount);

        Reservation reservation = mock(Reservation.class);
        User user = mock(User.class);
        TimeSlot timeSlot = mock(TimeSlot.class);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);
        when(reservation.getUser()).thenReturn(user);
        when(reservation.getReservationStatus()).thenReturn(ReservationStatus.CONFIRMED);
        when(reservation.getTimeSlot()).thenReturn(timeSlot);
        when(timeSlot.getTimeSlotId()).thenReturn(timeSlotId);
        when(timeSlotRepository.findByIdForUpdate(timeSlotId)).thenReturn(Optional.of(timeSlot));
        when(timeSlot.getSlotStatus()).thenReturn("OPEN");

        when(timeSlot.getSlotDate())
                .thenReturn(LocalDate.now().plusDays(1));

        ReservationException exception = assertThrows(
                ReservationException.class,
                () -> reservationService.updateReservation(username, reservationId, request)
        );

        assertEquals(
                ReservationErrorCode.INVALID_PEOPLE_COUNT,
                exception.getErrorCode()
        );

        verify(reservationRepository).findById(reservationId);
        verify(userRepository).findByUsername(username);
        verify(timeSlotRepository).findByIdForUpdate(timeSlotId);

        verify(timeSlot).getSlotStatus();
        verify(timeSlot, atLeastOnce()).getSlotDate();

        verify(timeSlot, never()).getReservedCount();
        verify(timeSlot, never()).getCapacity();

        verify(timeSlot, never()).increaseReservedCount(anyInt());
        verify(timeSlot, never()).decreaseReservedCount(anyInt());

        verify(reservation, never()).changeReservation(any(), anyInt());
        verify(reservation, never()).renewQrToken(anyString());

        verifyNoInteractions(
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("동일 회차에서 잔여 정원을 초과하여 인원을 증가시킬 수 없다")
    void updateReservation_sameSlot_capacityExceeded() {

        String username = "testUser";
        Long reservationId = 1L;
        Long userId = 1L;
        Long timeSlotId = 1L;

        // 동일 회차에서 기존 1명 -> 3명으로 변경 요청
        ReservationUpdateRequest request = new ReservationUpdateRequest(timeSlotId, 3);

        Reservation reservation = mock(Reservation.class);
        User user = mock(User.class);
        TimeSlot timeSlot = mock(TimeSlot.class);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        when(user.getId()).thenReturn(userId);
        when(reservation.getUser()).thenReturn(user);
        when(reservation.getReservationStatus()).thenReturn(ReservationStatus.CONFIRMED);

        // 기존 회차와 변경 대상 회차를 동일하게 설정
        when(reservation.getTimeSlot()).thenReturn(timeSlot);
        when(timeSlot.getTimeSlotId()).thenReturn(timeSlotId);

        when(timeSlotRepository.findByIdForUpdate(timeSlotId)).thenReturn(Optional.of(timeSlot));

        when(timeSlot.getSlotStatus()).thenReturn("OPEN");
        when(timeSlot.getSlotDate()).thenReturn(LocalDate.now().plusDays(1));

        // 기존 본인의 예약 인원: 1명
        when(reservation.getPeopleCount()).thenReturn(1);

        // 회차 전체 예약 인원: 9명
        when(timeSlot.getReservedCount()).thenReturn(9);

        // 회차 최대 정원: 10명
        when(timeSlot.getCapacity()).thenReturn(10);

        ReservationException exception = assertThrows(
                ReservationException.class,
                () -> reservationService.updateReservation(username, reservationId, request)
        );

        assertEquals(
                ReservationErrorCode.CAPACITY_EXCEEDED,
                exception.getErrorCode()
        );

        // 예약, 회원, 회차 조회 확인
        verify(reservationRepository).findById(reservationId);
        verify(userRepository).findByUsername(username);
        verify(timeSlotRepository).findByIdForUpdate(timeSlotId);

        verify(reservation).getPeopleCount();
        verify(timeSlot).getReservedCount();
        verify(timeSlot).getCapacity();

        // 정원 초과 이후 예약 인원 변경이 발생하지 않아야 함
        verify(timeSlot, never()).increaseReservedCount(anyInt());
        verify(timeSlot, never()).decreaseReservedCount(anyInt());

        // 예약 정보 변경 및 QR 토큰 재발급 미실행 확인
        verify(reservation, never()).changeReservation(any(), anyInt());
        verify(reservation, never()).renewQrToken(anyString());

        verifyNoInteractions(
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("동일 회차에서 예약 인원을 정상적으로 증가시킨다")
    void updateReservation_sameSlot_increasePeople() {

        String username = "testUser";
        Long reservationId = 1L;
        Long userId = 1L;
        Long timeSlotId = 1L;

        int oldPeopleCount = 1;
        int newPeopleCount = 3;
        int difference = newPeopleCount - oldPeopleCount;

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);

        ReservationUpdateRequest request = new ReservationUpdateRequest(timeSlotId, newPeopleCount);

        Reservation reservation = mock(Reservation.class);
        User user = mock(User.class);
        TimeSlot timeSlot = mock(TimeSlot.class);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        when(user.getId()).thenReturn(userId);
        when(reservation.getUser()).thenReturn(user);
        when(reservation.getReservationStatus()).thenReturn(ReservationStatus.CONFIRMED);

        // 기존 회차와 변경 회차가 동일한 상황
        when(reservation.getTimeSlot()).thenReturn(timeSlot);
        when(timeSlot.getTimeSlotId()).thenReturn(timeSlotId);

        when(timeSlotRepository.findByIdForUpdate(timeSlotId)).thenReturn(Optional.of(timeSlot));

        when(timeSlot.getSlotStatus()).thenReturn("OPEN");
        when(timeSlot.getSlotDate()).thenReturn(LocalDate.now().plusDays(1));

        // 기존 본인의 예약 인원: 1명
        when(reservation.getPeopleCount()).thenReturn(oldPeopleCount);

        // 기존 전체 예약 인원: 8명
        // 변경 후 전체 예약 인원: 10명
        when(timeSlot.getReservedCount()).thenReturn(8);
        when(timeSlot.getCapacity()).thenReturn(10);

        // 응답 DTO 변환에 필요한 데이터 설정
        when(reservation.getReservationId()).thenReturn(reservationId);
        when(reservation.getExpiresAt()).thenReturn(expiresAt);

        // Mock 객체는 실제 메서드를 실행하지 않으므로
        // changeReservation() 호출 시 예약 인원이 변경되도록 모의 처리
        doAnswer(invocation -> {

            int changedPeopleCount = invocation.getArgument(1);

            when(reservation.getPeopleCount()).thenReturn(changedPeopleCount);

            return null;

        }).when(reservation).changeReservation(eq(timeSlot), eq(newPeopleCount));

        // QR 토큰 재발급 시 전달받은 토큰이 저장되도록 모의 처리
        doAnswer(invocation -> {

            String token = invocation.getArgument(0);

            when(reservation.getQrToken()).thenReturn(token);

            return null;

        }).when(reservation).renewQrToken(anyString());

        ReservationResponse response =
                reservationService.updateReservation(username, reservationId, request);

        assertNotNull(response);

        verify(timeSlotRepository, times(1)).findByIdForUpdate(timeSlotId);

        // 전체 요청 인원 3명이 아니라 추가 인원 2명만 증가해야 함
        verify(timeSlot).increaseReservedCount(difference);

        // 인원 감소 처리는 발생하지 않아야 함
        verify(timeSlot, never()).decreaseReservedCount(anyInt());

        // 예약 정보 변경 확인
        verify(reservation).changeReservation(timeSlot, newPeopleCount);

        // 재발급된 QR 토큰 캡처
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);

        verify(reservation).renewQrToken(tokenCaptor.capture());

        String qrToken = tokenCaptor.getValue();

        // UUID 형식의 QR 토큰인지 검증
        assertNotNull(qrToken);
        assertFalse(qrToken.isBlank());
        assertDoesNotThrow(() -> UUID.fromString(qrToken));

        // 변경된 응답 DTO 검증
        assertEquals(reservationId, response.reservationId());
        assertEquals(timeSlotId, response.timeSlotId());
        assertEquals(newPeopleCount, response.peopleCount());

        // 예약 변경 후에도 CONFIRMED 상태 유지
        assertEquals(
                ReservationStatus.CONFIRMED,
                response.reservationStatus()
        );

        assertEquals(expiresAt, response.expiresAt());
        assertEquals(qrToken, response.qrToken());

        // 회차 정원이 변경되었으므로 변경 이벤트 1회 발행
        verify(eventPublisher).publishEvent(any(TimeSlotChangedEvent.class));

        // 별도의 예약 저장 호출 없이 엔티티 변경 처리
        verify(reservationRepository, never()).save(any());

        verifyNoInteractions(
                redisExpirationKeyService,
                reservationExpirationService
        );
    }

    @Test
    @DisplayName("동일 회차에서 예약 인원을 정상적으로 감소시킨다")
    void updateReservation_sameSlot_decreasePeople() {

        String username = "testUser";
        Long reservationId = 1L;
        Long userId = 1L;
        Long timeSlotId = 1L;

        int oldPeopleCount = 3;
        int newPeopleCount = 1;

        // 감소한 인원은 양수로 계산
        int difference = oldPeopleCount - newPeopleCount;

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);

        ReservationUpdateRequest request = new ReservationUpdateRequest(timeSlotId, newPeopleCount);

        Reservation reservation = mock(Reservation.class);
        User user = mock(User.class);
        TimeSlot timeSlot = mock(TimeSlot.class);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        when(user.getId()).thenReturn(userId);
        when(reservation.getUser()).thenReturn(user);
        when(reservation.getReservationStatus()).thenReturn(ReservationStatus.CONFIRMED);

        // 동일 회차 변경 설정
        when(reservation.getTimeSlot()).thenReturn(timeSlot);
        when(timeSlot.getTimeSlotId()).thenReturn(timeSlotId);

        when(timeSlotRepository.findByIdForUpdate(timeSlotId)).thenReturn(Optional.of(timeSlot));

        when(timeSlot.getSlotStatus()).thenReturn("OPEN");
        when(timeSlot.getSlotDate()).thenReturn(LocalDate.now().plusDays(1));
        when(reservation.getPeopleCount()).thenReturn(oldPeopleCount);

        // 응답 DTO 데이터 설정
        when(reservation.getReservationId()).thenReturn(reservationId);
        when(reservation.getExpiresAt()).thenReturn(expiresAt);

        // 예약 인원 변경 모의 처리
        doAnswer(invocation -> {

            int changedPeopleCount = invocation.getArgument(1);

            when(reservation.getPeopleCount()).thenReturn(changedPeopleCount);

            return null;

        }).when(reservation).changeReservation(eq(timeSlot), eq(newPeopleCount));

        // QR 토큰 재발급 모의 처리
        doAnswer(invocation -> {

            String token = invocation.getArgument(0);

            when(reservation.getQrToken()).thenReturn(token);

            return null;

        }).when(reservation).renewQrToken(anyString());

        ReservationResponse response = reservationService.updateReservation(username, reservationId, request);

        assertNotNull(response);

        // 동일 회차이므로 락 조회 1회 확인
        verify(timeSlotRepository).findByIdForUpdate(timeSlotId);

        verify(timeSlot, never()).getReservedCount();
        verify(timeSlot, never()).getCapacity();

        // 감소한 인원 2명만큼 정원 복구 확인
        verify(timeSlot).decreaseReservedCount(difference);

        // 인원 증가 처리는 발생하지 않아야 함
        verify(timeSlot, never()).increaseReservedCount(anyInt());

        // 예약 정보 변경 확인
        verify(reservation).changeReservation(timeSlot, newPeopleCount);

        // 재발급된 QR 토큰 캡처
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);

        verify(reservation).renewQrToken(tokenCaptor.capture());

        String qrToken = tokenCaptor.getValue();

        assertNotNull(qrToken);
        assertFalse(qrToken.isBlank());
        assertDoesNotThrow(() -> UUID.fromString(qrToken));

        // 응답 DTO 검증
        assertEquals(reservationId, response.reservationId());
        assertEquals(timeSlotId, response.timeSlotId());
        assertEquals(newPeopleCount, response.peopleCount());

        assertEquals(
                ReservationStatus.CONFIRMED,
                response.reservationStatus()
        );

        assertEquals(expiresAt, response.expiresAt());
        assertEquals(qrToken, response.qrToken());

        // 정원 복구 이후 회차 변경 이벤트 발행 확인
        verify(eventPublisher).publishEvent(any(TimeSlotChangedEvent.class));

        verify(reservationRepository, never()).save(any());

        verifyNoInteractions(
                redisExpirationKeyService,
                reservationExpirationService
        );
    }

    @Test
    @DisplayName("동일 회차에서 예약 인원이 같으면 정원을 변경하지 않는다")
    void updateReservation_sameSlot_samePeople() {

        String username = "testUser";
        Long reservationId = 1L;
        Long userId = 1L;
        Long timeSlotId = 1L;

        int peopleCount = 2;

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);

        // 기존 예약과 동일한 회차 및 인원으로 변경 요청
        ReservationUpdateRequest request = new ReservationUpdateRequest(timeSlotId, peopleCount);

        Reservation reservation = mock(Reservation.class);
        User user = mock(User.class);
        TimeSlot timeSlot = mock(TimeSlot.class);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        when(user.getId()).thenReturn(userId);
        when(reservation.getUser()).thenReturn(user);
        when(reservation.getReservationStatus()).thenReturn(ReservationStatus.CONFIRMED);
        when(reservation.getTimeSlot()).thenReturn(timeSlot);
        when(timeSlot.getTimeSlotId()).thenReturn(timeSlotId);
        when(timeSlotRepository.findByIdForUpdate(timeSlotId)).thenReturn(Optional.of(timeSlot));
        when(timeSlot.getSlotStatus()).thenReturn("OPEN");
        when(timeSlot.getSlotDate()).thenReturn(LocalDate.now().plusDays(1));

        // 기존 예약 인원과 변경 요청 인원이 동일
        when(reservation.getPeopleCount()).thenReturn(peopleCount);

        // 응답 DTO 변환 데이터 설정
        when(reservation.getReservationId()).thenReturn(reservationId);
        when(reservation.getExpiresAt()).thenReturn(expiresAt);

        // 현재 서비스는 인원이 동일하더라도 QR 토큰을 재발급함
        doAnswer(invocation -> {

            String token = invocation.getArgument(0);

            when(reservation.getQrToken()).thenReturn(token);

            return null;

        }).when(reservation).renewQrToken(anyString());

        ReservationResponse response = reservationService.updateReservation(username, reservationId, request);

        assertNotNull(response);

        // 동일 회차이므로 락 조회 1회 확인
        verify(timeSlotRepository).findByIdForUpdate(timeSlotId);

        verify(timeSlot, never()).getReservedCount();
        verify(timeSlot, never()).getCapacity();
        verify(timeSlot, never()).increaseReservedCount(anyInt());
        verify(timeSlot, never()).decreaseReservedCount(anyInt());
        verify(reservation).changeReservation(timeSlot, peopleCount);
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);

        verify(reservation).renewQrToken(tokenCaptor.capture());

        String qrToken = tokenCaptor.getValue();

        assertNotNull(qrToken);
        assertFalse(qrToken.isBlank());
        assertDoesNotThrow(() -> UUID.fromString(qrToken));

        // 기존 예약 정보가 유지되는지 확인
        assertEquals(reservationId, response.reservationId());
        assertEquals(timeSlotId, response.timeSlotId());
        assertEquals(peopleCount, response.peopleCount());

        assertEquals(
                ReservationStatus.CONFIRMED,
                response.reservationStatus()
        );

        assertEquals(expiresAt, response.expiresAt());
        assertEquals(qrToken, response.qrToken());

        // 별도의 저장 호출 미실행
        verify(reservationRepository, never()).save(any());

        verifyNoInteractions(
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("잔여 정원이 부족한 다른 회차로 변경할 수 없다")
    void updateReservation_differentSlot_capacityExceeded() {

        String username = "testUser";
        Long reservationId = 1L;
        Long userId = 1L;
        Long oldTimeSlotId = 1L;
        Long newTimeSlotId = 2L;

        int newPeopleCount = 2;

        // 기존 회차 1번에서 새 회차 2번으로 변경 요청
        ReservationUpdateRequest request = new ReservationUpdateRequest(newTimeSlotId, newPeopleCount);

        Reservation reservation = mock(Reservation.class);
        User user = mock(User.class);
        TimeSlot oldTimeSlot = mock(TimeSlot.class);
        TimeSlot newTimeSlot = mock(TimeSlot.class);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        when(user.getId()).thenReturn(userId);
        when(reservation.getUser()).thenReturn(user);
        when(reservation.getReservationStatus()).thenReturn(ReservationStatus.CONFIRMED);

        // 기존 예약과 연결된 회차 설정
        when(reservation.getTimeSlot()).thenReturn(oldTimeSlot);

        when(oldTimeSlot.getTimeSlotId()).thenReturn(oldTimeSlotId);
        when(newTimeSlot.getTimeSlotId()).thenReturn(newTimeSlotId);

        // 기존 회차와 변경 대상 회차 모두 락 조회 성공
        when(timeSlotRepository.findByIdForUpdate(oldTimeSlotId)).thenReturn(Optional.of(oldTimeSlot));
        when(timeSlotRepository.findByIdForUpdate(newTimeSlotId)).thenReturn(Optional.of(newTimeSlot));

        when(newTimeSlot.getSlotStatus()).thenReturn("OPEN");
        when(newTimeSlot.getSlotDate()).thenReturn(LocalDate.now().plusDays(1));

        // 새 회차의 현재 예약 인원: 9명
        // 최대 정원: 10명
        when(newTimeSlot.getReservedCount()).thenReturn(9);
        when(newTimeSlot.getCapacity()).thenReturn(10);

        ReservationException exception = assertThrows(
                ReservationException.class,
                () -> reservationService.updateReservation(username, reservationId, request)
        );

        assertEquals(
                ReservationErrorCode.CAPACITY_EXCEEDED,
                exception.getErrorCode()
        );

        // 예약 및 회원 조회 확인
        verify(reservationRepository).findById(reservationId);
        verify(userRepository).findByUsername(username);

        // 두 회차의 락 조회 확인
        verify(timeSlotRepository).findByIdForUpdate(oldTimeSlotId);
        verify(timeSlotRepository).findByIdForUpdate(newTimeSlotId);

        // 새 회차에 대해서만 정원 검증 수행
        verify(newTimeSlot).getReservedCount();
        verify(newTimeSlot).getCapacity();

        verify(oldTimeSlot, never()).getReservedCount();
        verify(oldTimeSlot, never()).getCapacity();

        // 변경 실패 시 기존 회차의 예약 인원이 감소하면 안 됨
        verify(oldTimeSlot, never()).decreaseReservedCount(anyInt());

        // 새 회차의 예약 인원도 증가하면 안 됨
        verify(newTimeSlot, never()).increaseReservedCount(anyInt());

        // 기존 예약 인원을 복구하는 단계까지 진행하지 않음
        verify(reservation, never()).getPeopleCount();

        // 예약 정보 변경 및 QR 토큰 재발급 미실행
        verify(reservation, never()).changeReservation(any(), anyInt());
        verify(reservation, never()).renewQrToken(anyString());

        verifyNoInteractions(
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("다른 회차로 정상 변경하고 기존 회차 정원을 복구한다")
    void updateReservation_differentSlot_success() {

        String username = "testUser";
        Long reservationId = 1L;
        Long userId = 1L;

        Long oldTimeSlotId = 1L;
        Long newTimeSlotId = 2L;

        int oldPeopleCount = 2;
        int newPeopleCount = 3;

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);

        // 기존 회차 1번 → 새 회차 2번
        // 기존 예약 인원 2명 → 변경 예약 인원 3명
        ReservationUpdateRequest request = new ReservationUpdateRequest(newTimeSlotId, newPeopleCount);

        Reservation reservation = mock(Reservation.class);
        User user = mock(User.class);
        TimeSlot oldTimeSlot = mock(TimeSlot.class);
        TimeSlot newTimeSlot = mock(TimeSlot.class);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        when(user.getId()).thenReturn(userId);
        when(reservation.getUser()).thenReturn(user);
        when(reservation.getReservationStatus()).thenReturn(ReservationStatus.CONFIRMED);

        // 기존 예약에 연결된 회차 및 인원 설정
        when(reservation.getTimeSlot()).thenReturn(oldTimeSlot);
        when(reservation.getPeopleCount()).thenReturn(oldPeopleCount);

        when(oldTimeSlot.getTimeSlotId()).thenReturn(oldTimeSlotId);
        when(newTimeSlot.getTimeSlotId()).thenReturn(newTimeSlotId);

        // 기존 회차와 새 회차 모두 락 조회 성공
        when(timeSlotRepository.findByIdForUpdate(oldTimeSlotId)).thenReturn(Optional.of(oldTimeSlot));
        when(timeSlotRepository.findByIdForUpdate(newTimeSlotId)).thenReturn(Optional.of(newTimeSlot));

        when(newTimeSlot.getSlotStatus()).thenReturn("OPEN");
        when(newTimeSlot.getSlotDate()).thenReturn(LocalDate.now().plusDays(1));

        // 새 회차 전체 예약 인원 7명 + 요청 인원 3명 = 10명
        when(newTimeSlot.getReservedCount()).thenReturn(7);
        when(newTimeSlot.getCapacity()).thenReturn(10);

        // 응답 DTO 변환 데이터 설정
        when(reservation.getReservationId()).thenReturn(reservationId);
        when(reservation.getExpiresAt()).thenReturn(expiresAt);

        // 예약 변경 메서드 호출 시 회차와 인원이 함께 변경되도록 모의 처리
        doAnswer(invocation -> {

            // changeReservation()의 첫 번째 인자는 변경할 회차
            TimeSlot changedTimeSlot = invocation.getArgument(0);

            // 두 번째 인자는 변경할 예약 인원
            int changedPeopleCount = invocation.getArgument(1);

            when(reservation.getTimeSlot()).thenReturn(changedTimeSlot);
            when(reservation.getPeopleCount()).thenReturn(changedPeopleCount);

            return null;

        }).when(reservation).changeReservation(eq(newTimeSlot), eq(newPeopleCount));

        // QR 토큰 재발급 모의 처리
        doAnswer(invocation -> {

            String token = invocation.getArgument(0);

            when(reservation.getQrToken()).thenReturn(token);

            return null;

        }).when(reservation).renewQrToken(anyString());

        ReservationResponse response =
                reservationService.updateReservation(username, reservationId, request);

        assertNotNull(response);

        verify(reservationRepository).findById(reservationId);
        verify(userRepository).findByUsername(username);
        verify(timeSlotRepository).findByIdForUpdate(oldTimeSlotId);
        verify(timeSlotRepository).findByIdForUpdate(newTimeSlotId);

        verify(newTimeSlot).getReservedCount();
        verify(newTimeSlot).getCapacity();

        // 기존 회차에서 기존 예약 인원 2명만큼 복구
        verify(oldTimeSlot).decreaseReservedCount(oldPeopleCount);

        // 새 회차에 변경 요청 인원 전체인 3명만큼 증가
        verify(newTimeSlot).increaseReservedCount(newPeopleCount);

        // 반대 방향의 정원 변경은 발생하지 않아야 함
        verify(oldTimeSlot, never()).increaseReservedCount(anyInt());
        verify(newTimeSlot, never()).decreaseReservedCount(anyInt());

        // 예약 정보가 새 회차 및 새 인원으로 변경되는지 확인
        verify(reservation).changeReservation(newTimeSlot, newPeopleCount);

        // 재발급된 QR 토큰 캡처 및 검증
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);

        verify(reservation).renewQrToken(tokenCaptor.capture());

        String qrToken = tokenCaptor.getValue();

        assertNotNull(qrToken);
        assertFalse(qrToken.isBlank());
        assertDoesNotThrow(() -> UUID.fromString(qrToken));

        // 응답 DTO 검증
        assertEquals(reservationId, response.reservationId());

        // 응답에는 기존 회차가 아닌 새 회차 ID가 반환되어야 함
        assertEquals(newTimeSlotId, response.timeSlotId());

        // 변경된 예약 인원 확인
        assertEquals(newPeopleCount, response.peopleCount());

        // 예약 변경 후에도 CONFIRMED 상태 유지
        assertEquals(
                ReservationStatus.CONFIRMED,
                response.reservationStatus()
        );

        assertEquals(expiresAt, response.expiresAt());
        assertEquals(qrToken, response.qrToken());

        // 기존 회차와 새 회차의 정원이 모두 변경되므로 회차 변경 이벤트가 총 2회 발행되어야 함
        verify(eventPublisher, times(2)).publishEvent(any(TimeSlotChangedEvent.class));

        // 별도의 예약 저장 호출 미실행 확인
        verify(reservationRepository, never()).save(any());

        verifyNoInteractions(
                redisExpirationKeyService,
                reservationExpirationService
        );
    }

    @Test
    @DisplayName("다른 회차로 변경할 때 작은 회차 ID부터 락을 획득한다")
    void updateReservation_lockOrder() {

        String username = "testUser";
        Long reservationId = 1L;
        Long userId = 1L;

        // 의도적으로 기존 회차 ID를 더 크게 설정
        Long oldTimeSlotId = 2L;
        Long newTimeSlotId = 1L;

        ReservationUpdateRequest request = new ReservationUpdateRequest(newTimeSlotId, 2);

        Reservation reservation = mock(Reservation.class);
        User user = mock(User.class);
        TimeSlot oldTimeSlot = mock(TimeSlot.class);
        TimeSlot newTimeSlot = mock(TimeSlot.class);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        when(user.getId()).thenReturn(userId);
        when(reservation.getUser()).thenReturn(user);
        when(reservation.getReservationStatus()).thenReturn(ReservationStatus.CONFIRMED);

        // 기존 예약은 ID가 더 큰 2번 회차에 연결
        when(reservation.getTimeSlot()).thenReturn(oldTimeSlot);
        when(oldTimeSlot.getTimeSlotId()).thenReturn(oldTimeSlotId);

        when(timeSlotRepository.findByIdForUpdate(newTimeSlotId)).thenReturn(Optional.of(newTimeSlot));
        when(timeSlotRepository.findByIdForUpdate(oldTimeSlotId)).thenReturn(Optional.of(oldTimeSlot));

        // 이번 테스트의 목적은 락 조회 순서 검증이므로 두 회차의 락 조회 이후 회차 운영 상태 검증에서 종료시킴
        when(newTimeSlot.getSlotStatus()).thenReturn("CLOSED");

        ReservationException exception = assertThrows(
                ReservationException.class,
                () -> reservationService.updateReservation(username, reservationId, request)
        );

        assertEquals(
                ReservationErrorCode.TIMESLOT_NOT_OPEN,
                exception.getErrorCode()
        );

        // InOrder: Mock 객체의 메서드 호출 순서를 검증하는 기능
        InOrder inOrder = inOrder(timeSlotRepository);

        // ID가 작은 1번 회차를 먼저 조회해야 함
        inOrder.verify(timeSlotRepository).findByIdForUpdate(newTimeSlotId);

        // 그 다음 ID가 큰 2번 회차를 조회해야 함
        inOrder.verify(timeSlotRepository).findByIdForUpdate(oldTimeSlotId);

        // 불필요한 추가 회차 조회가 없는지 확인
        verifyNoMoreInteractions(timeSlotRepository);

        // 회차 운영 상태 검증 실패 이후 정원 변경 미실행
        verify(oldTimeSlot, never()).decreaseReservedCount(anyInt());
        verify(newTimeSlot, never()).increaseReservedCount(anyInt());

        // 예약 정보 변경 및 QR 토큰 재발급 미실행 확인
        verify(reservation, never()).changeReservation(any(), anyInt());
        verify(reservation, never()).renewQrToken(anyString());

        verifyNoInteractions(
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    // ================ 예약 취소 ====================

    @Test
    @DisplayName("존재하지 않는 예약은 취소할 수 없다")
    void cancelReservation_reservationNotFound() {

        String username = "testUser";
        Long reservationId = 1L;

        // 존재하지 않는 예약으로 설정
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

        ReservationException exception = assertThrows(
                ReservationException.class,
                () -> reservationService.cancelReservation(username, reservationId)
        );

        assertEquals(
                ReservationErrorCode.RESERVATION_NOT_FOUND,
                exception.getErrorCode()
        );

        verify(reservationRepository).findById(reservationId);

        verifyNoInteractions(
                userRepository,
                timeSlotRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("존재하지 않는 회원은 예약을 취소할 수 없다")
    void cancelReservation_userNotFound() {

        String username = "testUser";
        Long reservationId = 1L;

        Reservation reservation = mock(Reservation.class);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        AuthException exception = assertThrows(
                AuthException.class,
                () -> reservationService.cancelReservation(username, reservationId)
        );

        assertEquals(
                AuthErrorCode.USER_NOT_FOUND,
                exception.getErrorCode()
        );

        // 예약 및 회원 조회 확인
        verify(reservationRepository).findById(reservationId);
        verify(userRepository).findByUsername(username);

        // 회원 조회 실패 이후 소유자 및 상태 검증 미실행
        verify(reservation, never()).getUser();
        verify(reservation, never()).getReservationStatus();

        // 예약 취소 미실행
        verify(reservation, never()).cancel();

        verifyNoInteractions(
                timeSlotRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("다른 회원의 예약은 취소할 수 없다")
    void cancelReservation_accessDenied() {

        String username = "testUser";
        Long reservationId = 1L;

        Long userId = 1L;
        Long ownerId = 2L;

        Reservation reservation = mock(Reservation.class);
        User user = mock(User.class);
        User owner = mock(User.class);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        // 로그인 회원과 예약 소유자가 다른 상황
        when(user.getId()).thenReturn(userId);
        when(owner.getId()).thenReturn(ownerId);
        when(reservation.getUser()).thenReturn(owner);

        AuthException exception = assertThrows(
                AuthException.class,
                () -> reservationService.cancelReservation(username, reservationId)
        );

        assertEquals(
                AuthErrorCode.ACCESS_DENIED,
                exception.getErrorCode()
        );

        verify(reservationRepository).findById(reservationId);
        verify(userRepository).findByUsername(username);

        // 예약 소유자 검증 확인
        verify(reservation).getUser();
        verify(user).getId();
        verify(owner).getId();

        // 소유자 검증 실패 이후 예약 상태 검증 미실행
        verify(reservation, never()).getReservationStatus();

        // 다른 회원의 예약이 취소되면 안 됨
        verify(reservation, never()).cancel();

        verifyNoInteractions(
                timeSlotRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = ReservationStatus.class,
            names = {"CONFIRMED"},
            mode = EnumSource.Mode.EXCLUDE
    )
    @DisplayName("CONFIRMED 상태가 아닌 예약은 취소할 수 없다")
    void cancelReservation_invalidStatus(ReservationStatus status) {

        String username = "testUser";
        Long reservationId = 1L;
        Long userId = 1L;

        Reservation reservation = mock(Reservation.class);
        User user = mock(User.class);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        when(user.getId()).thenReturn(userId);
        when(reservation.getUser()).thenReturn(user);

        // CONFIRMED를 제외한 모든 예약 상태를 순차적으로 설정
        // HOLD, CANCELLED, EXPIRED, USED
        when(reservation.getReservationStatus()).thenReturn(status);

        ReservationException exception = assertThrows(
                ReservationException.class,
                () -> reservationService.cancelReservation(username, reservationId)
        );

        assertEquals(
                ReservationErrorCode.INVALID_RESERVATION_STATUS,
                exception.getErrorCode()
        );

        verify(reservationRepository).findById(reservationId);
        verify(userRepository).findByUsername(username);

        // 로그인 회원과 예약 소유자가 동일한 Mock 객체이므로
        // 소유자 검증 과정에서 getId()가 2회 호출됨
        verify(reservation).getUser();
        verify(user, times(2)).getId();

        verify(reservation).getReservationStatus();

        // 예약 상태 검증 실패 이후 회차 조회 미실행
        verify(reservation, never()).getTimeSlot();

        // 예약 상태 변경 미실행
        verify(reservation, never()).cancel();

        verifyNoInteractions(
                timeSlotRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("예약과 연결된 회차가 존재하지 않으면 취소할 수 없다")
    void cancelReservation_timeSlotNotFound() {

        String username = "testUser";
        Long reservationId = 1L;
        Long userId = 1L;
        Long timeSlotId = 1L;

        Reservation reservation = mock(Reservation.class);
        User user = mock(User.class);
        TimeSlot timeSlot = mock(TimeSlot.class);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        when(user.getId()).thenReturn(userId);
        when(reservation.getUser()).thenReturn(user);
        when(reservation.getReservationStatus()).thenReturn(ReservationStatus.CONFIRMED);

        // 기존 예약과 연결된 회차 ID 설정
        when(reservation.getTimeSlot()).thenReturn(timeSlot);
        when(timeSlot.getTimeSlotId()).thenReturn(timeSlotId);

        // 실제 회차 Repository 조회는 실패하도록 설정
        when(timeSlotRepository.findByIdForUpdate(timeSlotId)).thenReturn(Optional.empty());

        ReservationException exception = assertThrows(
                ReservationException.class,
                () -> reservationService.cancelReservation(username, reservationId)
        );

        assertEquals(
                ReservationErrorCode.TIMESLOT_NOT_FOUND,
                exception.getErrorCode()
        );

        verify(reservationRepository).findById(reservationId);
        verify(userRepository).findByUsername(username);

        // 예약과 연결된 회차 ID 조회 확인
        verify(reservation).getTimeSlot();
        verify(timeSlot).getTimeSlotId();

        // 회차 비관적 락 조회 1회 확인
        verify(timeSlotRepository).findByIdForUpdate(timeSlotId);

        // 회차 조회 실패 이후 취소 가능 시간 검증 미실행
        verify(timeSlot, never()).getSlotDate();
        verify(timeSlot, never()).getStartTime();

        // 기존 예약 인원 조회 및 정원 복구 미실행
        verify(reservation, never()).getPeopleCount();
        verify(timeSlot, never()).decreaseReservedCount(anyInt());

        // 예약 취소 미실행
        verify(reservation, never()).cancel();

        verifyNoInteractions(
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("예약 취소 가능 시간이 지나면 취소할 수 없다")
    void cancelReservation_deadlinePassed() {

        String username = "testUser";
        Long reservationId = 1L;
        Long userId = 1L;
        Long timeSlotId = 1L;

        Reservation reservation = mock(Reservation.class);
        User user = mock(User.class);
        TimeSlot timeSlot = mock(TimeSlot.class);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        when(user.getId()).thenReturn(userId);
        when(reservation.getUser()).thenReturn(user);
        when(reservation.getReservationStatus()).thenReturn(ReservationStatus.CONFIRMED);
        when(reservation.getTimeSlot()).thenReturn(timeSlot);
        when(timeSlot.getTimeSlotId()).thenReturn(timeSlotId);
        when(timeSlotRepository.findByIdForUpdate(timeSlotId)).thenReturn(Optional.of(timeSlot));

        // 현재 시간으로부터 30분 뒤에 방문하는 회차 설정
        LocalDateTime visitStart = LocalDateTime.now().plusMinutes(30);

        // 회차 날짜와 시작 시간을 각각 설정
        when(timeSlot.getSlotDate()).thenReturn(visitStart.toLocalDate());

        when(timeSlot.getStartTime()).thenReturn(
                visitStart.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));

        ReservationException exception = assertThrows(
                ReservationException.class,
                () -> reservationService.cancelReservation(username, reservationId)
        );

        assertEquals(
                ReservationErrorCode.CANCELLATION_DEADLINE_PASSED,
                exception.getErrorCode()
        );

        verify(reservationRepository).findById(reservationId);
        verify(userRepository).findByUsername(username);
        verify(timeSlotRepository).findByIdForUpdate(timeSlotId);

        // 취소 마감 시간 계산에 필요한 날짜 및 시간 조회 확인
        verify(timeSlot).getSlotDate();
        verify(timeSlot).getStartTime();

        // 취소 가능 시간 검증 실패 이후
        // 기존 예약 인원 조회 및 정원 복구가 발생하면 안 됨
        verify(reservation, never()).getPeopleCount();
        verify(timeSlot, never()).decreaseReservedCount(anyInt());

        // 예약 상태 변경 미실행
        verify(reservation, never()).cancel();

        // 별도의 예약 저장 미실행
        verify(reservationRepository, never()).save(any());

        verifyNoInteractions(
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("예약을 정상적으로 취소하고 기존 회차 정원을 복구한다")
    void cancelReservation_success() {

        String username = "testUser";
        Long reservationId = 1L;
        Long userId = 1L;
        Long timeSlotId = 1L;

        int peopleCount = 2;

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);

        String qrToken = UUID.randomUUID().toString();

        Reservation reservation = mock(Reservation.class);
        User user = mock(User.class);
        TimeSlot timeSlot = mock(TimeSlot.class);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        when(user.getId()).thenReturn(userId);
        when(reservation.getUser()).thenReturn(user);
        when(reservation.getReservationStatus()).thenReturn(ReservationStatus.CONFIRMED);
        when(reservation.getTimeSlot()).thenReturn(timeSlot);
        when(timeSlot.getTimeSlotId()).thenReturn(timeSlotId);
        when(timeSlotRepository.findByIdForUpdate(timeSlotId)).thenReturn(Optional.of(timeSlot));

        // 현재 시간으로부터 2시간 뒤에 방문하는 회차 설정
        LocalDateTime visitStart = LocalDateTime.now().plusHours(2);

        when(timeSlot.getSlotDate()).thenReturn(visitStart.toLocalDate());

        when(timeSlot.getStartTime()).thenReturn(
                visitStart.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
        );

        // 기존 본인 예약 인원 설정
        when(reservation.getPeopleCount()).thenReturn(peopleCount);

        // 응답 DTO 변환에 필요한 데이터 설정
        when(reservation.getReservationId()).thenReturn(reservationId);
        when(reservation.getExpiresAt()).thenReturn(expiresAt);
        when(reservation.getQrToken()).thenReturn(qrToken);

        // Mock 객체는 실제 cancel() 메서드를 실행하지 않으므로
        // cancel() 호출 시 예약 상태가 CANCELLED로 변경되도록 모의 처리
        doAnswer(invocation -> {

            when(reservation.getReservationStatus()).thenReturn(ReservationStatus.CANCELLED);

            return null;

        }).when(reservation).cancel();

        ReservationResponse response =
                reservationService.cancelReservation(username, reservationId);

        assertNotNull(response);

        verify(reservationRepository).findById(reservationId);
        verify(userRepository).findByUsername(username);
        verify(timeSlotRepository, times(1)).findByIdForUpdate(timeSlotId);

        // 취소 가능 시간 검증에 필요한 데이터 조회 확인
        verify(timeSlot).getSlotDate();
        verify(timeSlot).getStartTime();

        // 기존 예약 인원 전체인 2명만큼 정원 복구 확인
        verify(timeSlot, times(1)).decreaseReservedCount(peopleCount);

        // 예약 취소이므로 예약 인원 증가가 발생하면 안 됨
        verify(timeSlot, never()).increaseReservedCount(anyInt());

        // 예약 취소 메서드 1회 호출 확인
        verify(reservation, times(1)).cancel();

        // 정원이 변경되었으므로 회차 변경 이벤트 발행
        verify(eventPublisher, times(1)).publishEvent(any(TimeSlotChangedEvent.class));

        // 취소 처리 순서 검증
        InOrder inOrder = inOrder(timeSlot, eventPublisher, reservation);

        inOrder.verify(timeSlot).decreaseReservedCount(peopleCount);

        inOrder.verify(eventPublisher).publishEvent(any(TimeSlotChangedEvent.class));

        inOrder.verify(reservation).cancel();

        // 응답 DTO 검증
        assertEquals(reservationId, response.reservationId());
        assertEquals(timeSlotId, response.timeSlotId());

        // 예약 인원은 이력 보존을 위해 기존 값 유지
        assertEquals(peopleCount, response.peopleCount());

        // 예약 상태가 CONFIRMED에서 CANCELLED로 변경되었는지 검증
        assertEquals(
                ReservationStatus.CANCELLED,
                response.reservationStatus()
        );

        assertEquals(expiresAt, response.expiresAt());

        // 취소 시 QR 토큰 재발급은 발생하지 않음
        verify(reservation, never()).renewQrToken(anyString());

        // 이번 Mock 설정에서는 기존 QR 토큰이 유지됨
        assertEquals(qrToken, response.qrToken());

        // JPA Dirty Checking을 활용하므로 별도의 save() 호출 미실행
        verify(reservationRepository, never()).save(any());

        verifyNoInteractions(
                redisExpirationKeyService,
                reservationExpirationService
        );
    }

    // =========== QR ============

    @Test
    @DisplayName("존재하지 않는 예약의 QR 코드는 조회할 수 없다")
    void getQr_reservationNotFound() {

        String username = "testUser";
        Long reservationId = 1L;

        // 예약 조회 실패 설정
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

        ReservationException exception = assertThrows(
                ReservationException.class,
                () -> reservationService.getQr(username, reservationId)
        );

        assertEquals(
                ReservationErrorCode.RESERVATION_NOT_FOUND,
                exception.getErrorCode()
        );

        verify(reservationRepository).findById(reservationId);

        verifyNoInteractions(
                userRepository,
                timeSlotRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("존재하지 않는 회원은 QR 코드를 조회할 수 없다")
    void getQr_userNotFound() {

        String username = "testUser";
        Long reservationId = 1L;

        Reservation reservation = mock(Reservation.class);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        AuthException exception = assertThrows(
                AuthException.class,
                () -> reservationService.getQr(username, reservationId)
        );

        assertEquals(
                AuthErrorCode.USER_NOT_FOUND,
                exception.getErrorCode()
        );

        verify(reservationRepository).findById(reservationId);
        verify(userRepository).findByUsername(username);

        // 회원 조회 실패 이후 소유자 및 예약 상태 검증 미실행
        verify(reservation, never()).getUser();
        verify(reservation, never()).getReservationStatus();

        // QR 토큰 조회까지 진행하지 않아야 함
        verify(reservation, never()).getQrToken();

        verifyNoInteractions(
                timeSlotRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("다른 회원의 예약 QR 코드는 조회할 수 없다")
    void getQr_accessDenied() {

        String username = "testUser";
        Long reservationId = 1L;

        Long userId = 1L;
        Long ownerId = 2L;

        Reservation reservation = mock(Reservation.class);
        User user = mock(User.class);
        User owner = mock(User.class);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        // 로그인 회원과 예약 소유자가 다른 상황
        when(user.getId()).thenReturn(userId);
        when(owner.getId()).thenReturn(ownerId);
        when(reservation.getUser()).thenReturn(owner);

        AuthException exception = assertThrows(
                AuthException.class,
                () -> reservationService.getQr(username, reservationId)
        );

        assertEquals(
                AuthErrorCode.ACCESS_DENIED,
                exception.getErrorCode()
        );

        verify(reservationRepository).findById(reservationId);
        verify(userRepository).findByUsername(username);

        // 예약 소유자 검증 확인
        verify(reservation).getUser();
        verify(user).getId();
        verify(owner).getId();

        // 접근 권한 검증 실패 이후 상태 및 QR 토큰 조회 미실행
        verify(reservation, never()).getReservationStatus();
        verify(reservation, never()).getQrToken();

        verifyNoInteractions(
                timeSlotRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = ReservationStatus.class,
            names = {"CONFIRMED"},
            mode = EnumSource.Mode.EXCLUDE
    )
    @DisplayName("CONFIRMED 상태가 아닌 예약은 QR 코드를 조회할 수 없다")
    void getQr_invalidStatus(ReservationStatus status) {

        String username = "testUser";
        Long reservationId = 1L;
        Long userId = 1L;

        Reservation reservation = mock(Reservation.class);
        User user = mock(User.class);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        when(user.getId()).thenReturn(userId);
        when(reservation.getUser()).thenReturn(user);

        // CONFIRMED를 제외한 예약 상태를 순차적으로 설정
        when(reservation.getReservationStatus()).thenReturn(status);

        ReservationException exception = assertThrows(
                ReservationException.class,
                () -> reservationService.getQr(username, reservationId)
        );

        assertEquals(
                ReservationErrorCode.INVALID_RESERVATION_STATUS,
                exception.getErrorCode()
        );

        verify(reservationRepository).findById(reservationId);
        verify(userRepository).findByUsername(username);

        // 동일한 회원 Mock 객체의 ID가 2회 조회됨
        verify(reservation).getUser();
        verify(user, times(2)).getId();

        verify(reservation).getReservationStatus();
        verify(reservation, never()).getQrToken();

        verifyNoInteractions(
                timeSlotRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("QR 토큰이 존재하지 않으면 QR 코드를 조회할 수 없다")
    void getQr_tokenNotFound() {

        String username = "testUser";
        Long reservationId = 1L;
        Long userId = 1L;

        Reservation reservation = mock(Reservation.class);
        User user = mock(User.class);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        when(user.getId()).thenReturn(userId);
        when(reservation.getUser()).thenReturn(user);
        when(reservation.getReservationStatus()).thenReturn(ReservationStatus.CONFIRMED);

        // 예약은 확정되었지만 QR 토큰이 존재하지 않는 상황
        when(reservation.getQrToken()).thenReturn(null);

        ReservationException exception = assertThrows(
                ReservationException.class,
                () -> reservationService.getQr(username, reservationId)
        );

        assertEquals(
                ReservationErrorCode.QR_TOKEN_NOT_FOUND,
                exception.getErrorCode()
        );

        verify(reservationRepository).findById(reservationId);
        verify(userRepository).findByUsername(username);

        verify(reservation).getUser();
        verify(user, times(2)).getId();
        verify(reservation).getReservationStatus();

        // QR 토큰 존재 여부 검증
        verify(reservation).getQrToken();

        // QR 조회는 읽기 전용 기능이므로
        // 예약 상태 변경 및 QR 토큰 재발급이 발생하면 안 됨
        verify(reservation, never()).renewQrToken(anyString());
        verify(reservation, never()).cancel();
        verify(reservationRepository, never()).save(any());

        verifyNoInteractions(
                timeSlotRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("정상적으로 QR 코드를 생성하고 QR 토큰을 확인한다")
    void getQr_success() throws Exception {

        String username = "testUser";
        Long reservationId = 1L;
        Long userId = 1L;

        // 실제 QR 코드에 들어갈 테스트용 토큰 생성
        String qrToken = UUID.randomUUID().toString();

        Reservation reservation = mock(Reservation.class);
        User user = mock(User.class);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        when(user.getId()).thenReturn(userId);
        when(reservation.getUser()).thenReturn(user);
        when(reservation.getReservationStatus()).thenReturn(ReservationStatus.CONFIRMED);

        // QR 이미지에 삽입될 토큰 설정
        when(reservation.getQrToken()).thenReturn(qrToken);

        // 실제 ZXing 라이브러리를 사용하여 QR 이미지 생성
        byte[] response = reservationService.getQr(username, reservationId);

        // 이미지 데이터가 정상적으로 반환되었는지 검증
        assertNotNull(response);
        assertTrue(response.length > 0);

        // PNG 파일의 고유 시그니처 검증
        byte[] pngSignature = {
                (byte) 0x89,
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A
        };

        assertArrayEquals(
                pngSignature, Arrays.copyOf(response, 8)
        );

        // byte[]를 실제 이미지 객체로 변환
        // 정상적인 이미지 데이터인지 검증하기 위한 과정
        BufferedImage image = ImageIO.read(
                new ByteArrayInputStream(response)
        );

        assertNotNull(image);

        // 서비스에서 지정한 이미지 크기 검증
        assertEquals(300, image.getWidth());
        assertEquals(300, image.getHeight());

        // BufferedImage를 ZXing에서 읽을 수 있는 형태로 변환
        BufferedImageLuminanceSource source = new BufferedImageLuminanceSource(image);

        // QR 코드의 명암 정보를 흑백 데이터로 변환
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

        // 실제 QR 코드를 디코딩하여 내부 데이터 추출
        Result result = new MultiFormatReader().decode(bitmap);

        // QR_CODE 형식으로 생성되었는지 확인
        assertEquals(
                BarcodeFormat.QR_CODE,
                result.getBarcodeFormat()
        );

        // QR 코드 내부의 문자열과 기존 QR 토큰이 일치하는지 검증
        assertEquals(qrToken, result.getText());

        verify(reservationRepository).findById(reservationId);
        verify(userRepository).findByUsername(username);
        verify(reservation).getUser();
        verify(user, times(2)).getId();
        verify(reservation).getReservationStatus();
        verify(reservation).getQrToken();

        // QR 조회는 읽기 전용 기능이므로
        // 기존 QR 토큰을 변경하거나 예약을 취소하면 안 됨
        verify(reservation, never()).renewQrToken(anyString());
        verify(reservation, never()).cancel();
        verify(reservation, never()).changeReservation(any(), anyInt());

        // 별도의 예약 저장 미실행
        verify(reservationRepository, never()).save(any());

        verifyNoInteractions(
                timeSlotRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    // =========== 예약 일괄 취소 =============

    @Test
    @DisplayName("존재하지 않는 회차의 예약은 일괄 취소할 수 없다")
    void cancelAllBySlot_timeSlotNotFound() {

        Long timeSlotId = 1L;

        // 회차 조회 실패 설정
        when(timeSlotRepository.findByIdForUpdate(timeSlotId)).thenReturn(Optional.empty());

        TimeSlotException exception = assertThrows(
                TimeSlotException.class,
                () -> reservationService.cancelAllBySlot(timeSlotId)
        );

        assertEquals(
                TimeSlotErrorCode.TIMESLOT_NOT_FOUND,
                exception.getErrorCode()
        );

        verify(timeSlotRepository).findByIdForUpdate(timeSlotId);

        verifyNoInteractions(
                reservationRepository,
                userRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("활성 예약이 없어도 회차 예약 인원을 정상적으로 초기화한다")
    void cancelAllBySlot_noActiveReservations() {

        Long timeSlotId = 1L;

        TimeSlot timeSlot = mock(TimeSlot.class);

        List<ReservationStatus> activeStatuses = List.of(
                ReservationStatus.HOLD,
                ReservationStatus.CONFIRMED
        );

        when(timeSlotRepository.findByIdForUpdate(timeSlotId))
                .thenReturn(Optional.of(timeSlot));

        // 해당 회차에 활성 예약이 없는 상황
        when(reservationRepository.findActiveReservationsByTimeSlot(
                timeSlotId,
                activeStatuses
        )).thenReturn(List.of());

        // 반환 타입이 void이므로 예외 없이 실행되는지 확인
        assertDoesNotThrow(
                () -> reservationService.cancelAllBySlot(timeSlotId)
        );

        verify(timeSlotRepository).findByIdForUpdate(timeSlotId);

        // HOLD와 CONFIRMED 상태의 예약만 조회하는지 검증
        verify(reservationRepository)
                .findActiveReservationsByTimeSlot(
                        timeSlotId,
                        activeStatuses
                );

        // 활성 예약 목록이 비어 있어도
        // 최종적으로 회차 예약 인원 초기화 메서드는 실행됨
        verify(timeSlot).resetReservedCount();

        // 개별 인원 증가 및 감소 방식은 사용하지 않음
        verify(timeSlot, never()).increaseReservedCount(anyInt());
        verify(timeSlot, never()).decreaseReservedCount(anyInt());

        // 별도의 예약 및 회차 저장 미실행
        verify(reservationRepository, never()).save(any());
        verify(timeSlotRepository, never()).save(any());

        verifyNoInteractions(
                userRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("HOLD 상태 예약을 일괄 취소하고 Redis 만료 키를 삭제한다")
    void cancelAllBySlot_holdReservations() {

        Long timeSlotId = 1L;

        Long firstReservationId = 1L;
        Long secondReservationId = 2L;

        TimeSlot timeSlot = mock(TimeSlot.class);
        Reservation firstReservation = mock(Reservation.class);
        Reservation secondReservation = mock(Reservation.class);
        List<ReservationStatus> activeStatuses = List.of(
                ReservationStatus.HOLD,
                ReservationStatus.CONFIRMED
        );

        // 회차 비관적 락 조회 성공
        when(timeSlotRepository.findByIdForUpdate(timeSlotId)).thenReturn(Optional.of(timeSlot));

        // 해당 회차에 HOLD 상태의 예약 2개가 존재하는 상황
        when(reservationRepository.findActiveReservationsByTimeSlot(
                timeSlotId,
                activeStatuses
        )).thenReturn(
                List.of(firstReservation, secondReservation)
        );

        // 첫 번째 예약 상태 및 ID 설정
        when(firstReservation.getReservationStatus())
                .thenReturn(ReservationStatus.HOLD);

        when(firstReservation.getReservationId())
                .thenReturn(firstReservationId);

        // 두 번째 예약 상태 및 ID 설정
        when(secondReservation.getReservationStatus())
                .thenReturn(ReservationStatus.HOLD);

        when(secondReservation.getReservationId())
                .thenReturn(secondReservationId);

        assertDoesNotThrow(
                () -> reservationService.cancelAllBySlot(timeSlotId)
        );

        verify(timeSlotRepository).findByIdForUpdate(timeSlotId);

        verify(reservationRepository)
                .findActiveReservationsByTimeSlot(
                        timeSlotId,
                        activeStatuses
                );

        verify(firstReservation).getReservationStatus();
        verify(secondReservation).getReservationStatus();
        verify(firstReservation).getReservationId();
        verify(secondReservation).getReservationId();

        // HOLD 상태의 예약은 Redis 만료 키가 존재하므로
        // 각 예약에 해당하는 정확한 만료 키를 삭제해야 함
        verify(redisExpirationKeyService)
                .delete("reservation:hold:" + firstReservationId);

        verify(redisExpirationKeyService)
                .delete("reservation:hold:" + secondReservationId);

        // 모든 HOLD 예약 취소 확인
        verify(firstReservation).cancel();
        verify(secondReservation).cancel();

        // 모든 예약 취소 이후 회차 예약 인원 초기화
        verify(timeSlot).resetReservedCount();

        // 추가적인 Redis 호출이 발생하지 않았는지 확인
        verifyNoMoreInteractions(redisExpirationKeyService);

        // 처리 순서 검증
        InOrder inOrder = inOrder(
                redisExpirationKeyService,
                firstReservation,
                secondReservation,
                timeSlot
        );

        // 첫 번째 예약의 만료 키 삭제 후 취소
        inOrder.verify(redisExpirationKeyService).delete("reservation:hold:" + firstReservationId);

        inOrder.verify(firstReservation).cancel();

        // 두 번째 예약의 만료 키 삭제 후 취소
        inOrder.verify(redisExpirationKeyService).delete("reservation:hold:" + secondReservationId);

        inOrder.verify(secondReservation).cancel();

        // 모든 예약 취소가 끝난 후 회차 정원 초기화
        inOrder.verify(timeSlot).resetReservedCount();

        // 일괄 취소는 개별 예약 인원만큼 감소시키는 방식이 아님
        verify(timeSlot, never()).decreaseReservedCount(anyInt());
        verify(timeSlot, never()).increaseReservedCount(anyInt());

        verify(reservationRepository, never()).save(any());
        verify(timeSlotRepository, never()).save(any());

        verifyNoInteractions(
                userRepository,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("CONFIRMED 상태 예약은 Redis 만료 키 삭제 없이 일괄 취소한다")
    void cancelAllBySlot_confirmedReservations() {

        Long timeSlotId = 1L;

        TimeSlot timeSlot = mock(TimeSlot.class);
        Reservation firstReservation = mock(Reservation.class);
        Reservation secondReservation = mock(Reservation.class);
        List<ReservationStatus> activeStatuses = List.of(
                ReservationStatus.HOLD,
                ReservationStatus.CONFIRMED
        );
        when(timeSlotRepository.findByIdForUpdate(timeSlotId)).thenReturn(Optional.of(timeSlot));
        when(reservationRepository.findActiveReservationsByTimeSlot(
                timeSlotId,
                activeStatuses
        )).thenReturn(
                List.of(firstReservation, secondReservation)
        );
        when(firstReservation.getReservationStatus()).thenReturn(ReservationStatus.CONFIRMED);
        when(secondReservation.getReservationStatus()).thenReturn(ReservationStatus.CONFIRMED);

        assertDoesNotThrow(
                () -> reservationService.cancelAllBySlot(timeSlotId)
        );

        verify(timeSlotRepository).findByIdForUpdate(timeSlotId);

        verify(reservationRepository)
                .findActiveReservationsByTimeSlot(
                        timeSlotId,
                        activeStatuses
                );

        verify(firstReservation).getReservationStatus();
        verify(secondReservation).getReservationStatus();
        verify(firstReservation, never()).getReservationId();
        verify(secondReservation, never()).getReservationId();
        verify(firstReservation).cancel();
        verify(secondReservation).cancel();
        verify(timeSlot).resetReservedCount();
        InOrder inOrder = inOrder(
                firstReservation,
                secondReservation,
                timeSlot
        );
        inOrder.verify(firstReservation).cancel();
        inOrder.verify(secondReservation).cancel();
        inOrder.verify(timeSlot).resetReservedCount();
        verify(timeSlot, never()).increaseReservedCount(anyInt());
        verify(timeSlot, never()).decreaseReservedCount(anyInt());

        verify(reservationRepository, never()).save(any());
        verify(timeSlotRepository, never()).save(any());

        verifyNoInteractions(
                userRepository,
                redisExpirationKeyService,
                reservationExpirationService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("HOLD와 CONFIRMED 예약이 혼합된 회차를 정상적으로 일괄 취소한다")
    void cancelAllBySlot_mixedReservations() {

        Long timeSlotId = 1L;

        TimeSlot timeSlot = mock(TimeSlot.class);

        Reservation firstHoldReservation = mock(Reservation.class);
        Reservation firstConfirmedReservation = mock(Reservation.class);
        Reservation secondHoldReservation = mock(Reservation.class);
        Reservation secondConfirmedReservation = mock(Reservation.class);
        List<ReservationStatus> activeStatuses = List.of(
                ReservationStatus.HOLD,
                ReservationStatus.CONFIRMED
        );
        when(timeSlotRepository.findByIdForUpdate(timeSlotId)).thenReturn(Optional.of(timeSlot));
        when(reservationRepository.findActiveReservationsByTimeSlot(
                timeSlotId,
                activeStatuses
        )).thenReturn(
                List.of(
                        firstHoldReservation,
                        firstConfirmedReservation,
                        secondHoldReservation,
                        secondConfirmedReservation
                )
        );

        // 첫 번째 HOLD 예약 설정
        when(firstHoldReservation.getReservationStatus()).thenReturn(ReservationStatus.HOLD);
        when(firstHoldReservation.getReservationId()).thenReturn(1L);
        when(firstConfirmedReservation.getReservationStatus()).thenReturn(ReservationStatus.CONFIRMED);

        // 두 번째 HOLD 예약 설정
        when(secondHoldReservation.getReservationStatus()).thenReturn(ReservationStatus.HOLD);
        when(secondHoldReservation.getReservationId()).thenReturn(3L);
        when(secondConfirmedReservation.getReservationStatus()).thenReturn(ReservationStatus.CONFIRMED);

        assertDoesNotThrow(
                () -> reservationService.cancelAllBySlot(timeSlotId)
        );

        verify(timeSlotRepository).findByIdForUpdate(timeSlotId);
        verify(reservationRepository)
                .findActiveReservationsByTimeSlot(
                        timeSlotId,
                        activeStatuses
                );
        verify(firstHoldReservation).getReservationStatus();
        verify(firstConfirmedReservation).getReservationStatus();
        verify(secondHoldReservation).getReservationStatus();
        verify(secondConfirmedReservation).getReservationStatus();

        // HOLD 상태인 예약의 ID만 조회되어야 함
        verify(firstHoldReservation).getReservationId();
        verify(secondHoldReservation).getReservationId();

        verify(firstConfirmedReservation, never()).getReservationId();
        verify(secondConfirmedReservation, never()).getReservationId();

        // HOLD 상태인 예약의 Redis 만료 키만 삭제
        verify(redisExpirationKeyService).delete("reservation:hold:1");
        verify(redisExpirationKeyService).delete("reservation:hold:3");

        // CONFIRMED 예약에 대한 잘못된 Redis 삭제 호출이 추가로 발생하지 않았는지 확인
        verifyNoMoreInteractions(redisExpirationKeyService);

        // 예약 상태에 관계없이 조회된 활성 예약은 모두 취소
        verify(firstHoldReservation).cancel();
        verify(firstConfirmedReservation).cancel();
        verify(secondHoldReservation).cancel();
        verify(secondConfirmedReservation).cancel();

        // 모든 예약 취소 이후 회차 예약 인원 초기화
        verify(timeSlot, times(1)).resetReservedCount();

        // 전체 처리 순서 검증
        InOrder inOrder = inOrder(
                timeSlotRepository,
                reservationRepository,
                redisExpirationKeyService,
                firstHoldReservation,
                firstConfirmedReservation,
                secondHoldReservation,
                secondConfirmedReservation,
                timeSlot
        );

        // 회차 락 조회 이후 활성 예약 목록 조회
        inOrder.verify(timeSlotRepository).findByIdForUpdate(timeSlotId);
        inOrder.verify(reservationRepository)
                .findActiveReservationsByTimeSlot(
                        timeSlotId,
                        activeStatuses
                );

        // 첫 번째 HOLD: Redis 삭제 -> 예약 취소
        inOrder.verify(redisExpirationKeyService).delete("reservation:hold:1");
        inOrder.verify(firstHoldReservation).cancel();
        // 첫 번째 CONFIRMED: 예약 취소만 실행
        inOrder.verify(firstConfirmedReservation).cancel();

        // 두 번째 HOLD: Redis 삭제 -> 예약 취소
        inOrder.verify(redisExpirationKeyService).delete("reservation:hold:3");
        inOrder.verify(secondHoldReservation).cancel();
        // 두 번째 CONFIRMED: 예약 취소만 실행
        inOrder.verify(secondConfirmedReservation).cancel();

        // 모든 예약 취소 이후 정원 초기화
        inOrder.verify(timeSlot).resetReservedCount();

        verify(timeSlot, never()).increaseReservedCount(anyInt());
        verify(timeSlot, never()).decreaseReservedCount(anyInt());
        verify(reservationRepository, never()).save(any());
        verify(timeSlotRepository, never()).save(any());

        verifyNoInteractions(
                userRepository,
                reservationExpirationService,
                eventPublisher
        );
    }
}

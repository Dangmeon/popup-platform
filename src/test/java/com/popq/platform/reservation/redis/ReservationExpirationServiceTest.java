package com.popq.platform.reservation.redis;

import com.popq.platform.reservation.entity.Reservation;
import com.popq.platform.reservation.entity.ReservationStatus;
import com.popq.platform.reservation.exception.ReservationErrorCode;
import com.popq.platform.reservation.exception.ReservationException;
import com.popq.platform.reservation.repository.ReservationRepository;
import com.popq.platform.timeslot.entity.TimeSlot;
import com.popq.platform.timeslot.repository.TimeSlotRepository;
import com.popq.platform.timeslot.dto.TimeSlotChangedEvent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationExpirationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private TimeSlotRepository timeSlotRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ReservationExpirationService reservationExpirationService;

    @Test
    @DisplayName("존재하지 않는 예약은 만료 처리할 수 없다")
    void expireReservation_reservationNotFound() {

        Long reservationId = 1L;

        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.empty());

        ReservationException exception = assertThrows(
                ReservationException.class,
                () -> reservationExpirationService.expireReservation(reservationId)
        );

        assertEquals(
                ReservationErrorCode.RESERVATION_NOT_FOUND,
                exception.getErrorCode()
        );

        verify(reservationRepository).findByIdForUpdate(reservationId);

        verifyNoInteractions(
                timeSlotRepository,
                eventPublisher
        );
    }

    @Test
    @DisplayName("HOLD 상태가 아닌 예약은 만료 처리하지 않는다")
    void expireReservation_invalidStatus() {

        Long reservationId = 1L;

        Reservation reservation = mock(Reservation.class);

        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(reservation.getReservationStatus()).thenReturn(ReservationStatus.CONFIRMED);

        assertDoesNotThrow(
                () -> reservationExpirationService.expireReservation(reservationId)
        );

        verify(reservationRepository).findByIdForUpdate(reservationId);
        verify(reservation).getReservationStatus();

        // HOLD가 아니므로 만료 시간 검증까지 진행하지 않음
        verify(reservation, never()).getExpiresAt();

        // 예약 만료 및 정원 복구가 발생하면 안 됨
        verify(reservation, never()).expire();

        verifyNoInteractions(
                timeSlotRepository,
                eventPublisher
        );
    }

    @Test
    @DisplayName("만료 시간이 지나지 않은 예약은 만료 처리하지 않는다")
    void expireReservation_notExpired() {

        Long reservationId = 1L;

        Reservation reservation = mock(Reservation.class);

        // 현재 시간보다 5분 뒤에 만료되는 예약
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);

        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(reservation.getReservationStatus()).thenReturn(ReservationStatus.HOLD);

        when(reservation.getExpiresAt()).thenReturn(expiresAt);

        assertDoesNotThrow(
                () -> reservationExpirationService.expireReservation(reservationId)
        );

        verify(reservationRepository).findByIdForUpdate(reservationId);

        verify(reservation).getReservationStatus();
        verify(reservation).getExpiresAt();

        // 만료 시간이 지나지 않았으므로 만료 처리 미실행
        verify(reservation, never()).expire();

        verifyNoInteractions(
                timeSlotRepository,
                eventPublisher
        );
    }

    @Test
    @DisplayName("만료된 HOLD 예약의 정원을 복구하고 만료 처리한다")
    void expireReservation_success() {

        Long reservationId = 1L;
        Long timeSlotId = 2L;

        int peopleCount = 2;

        // 현재 시간보다 5분 전에 만료된 예약
        LocalDateTime expiresAt = LocalDateTime.now().minusMinutes(5);

        Reservation reservation = mock(Reservation.class);
        TimeSlot timeSlot = mock(TimeSlot.class);
        when(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(reservation.getReservationStatus()).thenReturn(ReservationStatus.HOLD);
        when(reservation.getExpiresAt()).thenReturn(expiresAt);

        when(reservation.getTimeSlot()).thenReturn(timeSlot);
        when(timeSlot.getTimeSlotId()).thenReturn(timeSlotId);
        when(timeSlotRepository.findByIdForUpdate(timeSlotId)).thenReturn(Optional.of(timeSlot));

        when(reservation.getPeopleCount()).thenReturn(peopleCount);

        assertDoesNotThrow(
                () -> reservationExpirationService.expireReservation(reservationId)
        );

        verify(reservationRepository).findByIdForUpdate(reservationId);
        verify(timeSlotRepository).findByIdForUpdate(timeSlotId);

        verify(timeSlot).decreaseReservedCount(peopleCount);
        verify(reservation).expire();

        // 회차 변경 이벤트 발행 및 내부 데이터 검증
        ArgumentCaptor<TimeSlotChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(TimeSlotChangedEvent.class);

        verify(eventPublisher).publishEvent(eventCaptor.capture());

        TimeSlotChangedEvent event = eventCaptor.getValue();

        assertEquals(timeSlotId, event.timeSlotId());

        // 핵심 처리 순서 검증
        InOrder inOrder = inOrder(
                reservationRepository,
                timeSlotRepository,
                timeSlot,
                reservation,
                eventPublisher
        );

        inOrder.verify(reservationRepository).findByIdForUpdate(reservationId);
        inOrder.verify(timeSlotRepository).findByIdForUpdate(timeSlotId);
        inOrder.verify(timeSlot).decreaseReservedCount(peopleCount);
        inOrder.verify(reservation).expire();

        // 회차 변경 이벤트 발행
        inOrder.verify(eventPublisher).publishEvent(any(TimeSlotChangedEvent.class));

        // 불필요한 처리 방지
        verify(timeSlot, never()).increaseReservedCount(anyInt());
        verify(reservation, never()).cancel();

        verify(reservationRepository, never()).save(any());
        verify(timeSlotRepository, never()).save(any());
    }
}
package com.popq.platform.reservation.service;


import com.popq.platform.reservation.entity.Reservation;
import com.popq.platform.reservation.entity.ReservationStatus;
import com.popq.platform.reservation.redis.ReservationExpirationService;
import com.popq.platform.reservation.repository.ReservationRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationExpirationSchedulerTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationExpirationService reservationExpirationService;

    @InjectMocks
    private ReservationExpirationScheduler reservationExpirationScheduler;

    @Test
    @DisplayName("만료 대상 예약이 없으면 만료 서비스를 호출하지 않는다")
    void expireHoldReservations_noExpiredReservations() {

        // 만료 대상 예약이 없는 상황
        when(reservationRepository.findExpiredReservations(
                eq(ReservationStatus.HOLD), any(LocalDateTime.class)
        )).thenReturn(List.of());

        assertDoesNotThrow(
                () -> reservationExpirationScheduler.expireHoldReservations()
        );

        // HOLD 상태의 만료 대상 예약 조회 확인
        verify(reservationRepository).findExpiredReservations(
                        eq(ReservationStatus.HOLD),
                        any(LocalDateTime.class)
                );

        verifyNoInteractions(reservationExpirationService);
    }

    @Test
    @DisplayName("만료 대상 예약들의 만료 서비스를 정상적으로 호출한다")
    void expireHoldReservations_success() {

        Long firstReservationId = 1L;
        Long secondReservationId = 2L;

        Reservation firstReservation = mock(Reservation.class);
        Reservation secondReservation = mock(Reservation.class);
        when(firstReservation.getReservationId()).thenReturn(firstReservationId);
        when(secondReservation.getReservationId()).thenReturn(secondReservationId);

        // 만료 대상 예약 2개가 존재하는 상황
        when(reservationRepository.findExpiredReservations(
                eq(ReservationStatus.HOLD),
                any(LocalDateTime.class)
        )).thenReturn(
                List.of(firstReservation, secondReservation)
        );

        // 조회 시간 검증을 위한 시작 시각
        LocalDateTime before = LocalDateTime.now();

        assertDoesNotThrow(
                () -> reservationExpirationScheduler.expireHoldReservations()
        );

        LocalDateTime after = LocalDateTime.now();

        // 조회 조건으로 전달된 시간 추출
        ArgumentCaptor<LocalDateTime> timeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        verify(reservationRepository).findExpiredReservations(
                        eq(ReservationStatus.HOLD),
                        timeCaptor.capture()
        );

        LocalDateTime queriedAt = timeCaptor.getValue();

        // 실제 조회 시간이 스케줄러 실행 시각 범위에 포함되는지 확인
        assertFalse(queriedAt.isBefore(before));
        assertFalse(queriedAt.isAfter(after));

        // 조회된 각 예약의 만료 서비스 호출 확인
        verify(reservationExpirationService).expireReservation(firstReservationId);
        verify(reservationExpirationService).expireReservation(secondReservationId);

        verifyNoMoreInteractions(reservationExpirationService);
    }
}
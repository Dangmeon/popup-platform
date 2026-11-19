package com.popq.platform.reservation.service;

import com.popq.platform.entity.Reservation;
import com.popq.platform.entity.ReservationStatus;
import com.popq.platform.reservation.redis.ReservationExpirationService;
import com.popq.platform.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ReservationExpirationScheduler {

    private static final long FALLBACK_INTERVAL = 20 * 60 * 1000L;

    private final ReservationRepository reservationRepository;
    private final ReservationExpirationService reservationExpirationService;

    // List는 한 번의 조회 시점에 만료 대상 예약이 여러 개일 수 있어서 사용
    // for문은 그 리스트 안의 만료 대상 예약들을 하나씩 꺼내 실제 만료 처리 서비스에 넘기기 위해 사용
    // 만료 여부 자체는 Repository 조회 조건에서 이미 필터링

    @Scheduled(fixedRate = FALLBACK_INTERVAL)
    public void expireHoldReservations() {

        List<Reservation> expiredReservations = reservationRepository.findExpiredReservations(
                ReservationStatus.HOLD,
                LocalDateTime.now()
        );

        for (Reservation reservation : expiredReservations) {
            reservationExpirationService.expireReservation(reservation.getReservationId());
        }
    }
}

package com.popq.platform.repository;

import com.popq.platform.entity.Reservation;
import com.popq.platform.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    boolean existsByMemberIdAndTimeSlotIdAndReservationStatusIn(
            Long memberId, Long timeSlotId, List<ReservationStatus> reservationStatus
    );

    List<Reservation> findByReservationStatusAndExpiresAtBefore(
            ReservationStatus reservationStatus,
            LocalDateTime now
    );
}

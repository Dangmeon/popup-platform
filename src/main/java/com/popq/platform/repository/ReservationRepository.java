package com.popq.platform.repository;

import com.popq.platform.entity.Reservation;
import com.popq.platform.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Query("""
        SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
        FROM Reservation r
        WHERE r.user.id = :memberId
            AND r.timeSlot.timeSlotId = :timeSlotId
            AND r.reservationStatus IN :statuses
    """)
    boolean existsActiveReservation(
            @Param("memberId") Long memberId,
            @Param("timeSlotId") Long timeSlotId,
            @Param("statuses") List<ReservationStatus> statuses
    );

    @Query("""
        SELECT r
        FROM Reservation r
        WHERE r.reservationStatus = :status
            AND r.expiresAt < :now
    """)
    List<Reservation> findExpiredReservations(
            @Param("status") ReservationStatus status,
            @Param("now") LocalDateTime now
    );
}

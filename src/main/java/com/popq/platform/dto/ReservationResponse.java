package com.popq.platform.dto;

import com.popq.platform.entity.Reservation;
import com.popq.platform.entity.ReservationStatus;

import java.time.LocalDateTime;

public record ReservationResponse(
        Long reservationId,
        Long timeSlotId,
        int peopleCount,
        ReservationStatus reservationStatus,
        LocalDateTime expiresAt,
        String qrToken
) {

    public static ReservationResponse fromReservation(Reservation reservation) {
        return new ReservationResponse(
                reservation.getReservationId(),
                reservation.getTimeSlot().getTimeSlotId(),
                reservation.getPeopleCount(),
                reservation.getReservationStatus(),
                reservation.getExpiresAt(),
                reservation.getQrToken()
        );
    }
}

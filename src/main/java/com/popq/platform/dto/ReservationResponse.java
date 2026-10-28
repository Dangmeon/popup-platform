package com.popq.platform.dto;

import com.popq.platform.entity.ReservationStatus;

import java.time.LocalDateTime;

public record ReservationResponse(
        Long reservationId,
        Long timeSlotId,
        int peopleCount,
        ReservationStatus reservationStatus,
        LocalDateTime expiredAt,
        String qrToken
) {
}

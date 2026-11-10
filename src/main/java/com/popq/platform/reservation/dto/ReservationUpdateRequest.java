package com.popq.platform.reservation.dto;

public record ReservationUpdateRequest(
        Long timeSlotId,
        int peopleCount
) {
}

package com.popq.platform.dto;

public record ReservationUpdateRequest(
        Long timeSlotId,
        int peopleCount
) {
}

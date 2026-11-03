package com.popq.platform.entity;

public record ReservationHoldCreatedEvent(
        Long reservationId,
        int expireMinutes
) {
}
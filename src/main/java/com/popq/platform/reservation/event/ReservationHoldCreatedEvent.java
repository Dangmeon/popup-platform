package com.popq.platform.reservation.event;

public record ReservationHoldCreatedEvent(
        Long reservationId,
        int expireMinutes
) {
}
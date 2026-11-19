package com.popq.platform.order.event;

public record OrderPendingCreatedEvent(
        Long orderId,
        int expireMinutes
) {
}
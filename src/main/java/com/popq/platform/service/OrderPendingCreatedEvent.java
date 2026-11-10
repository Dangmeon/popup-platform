package com.popq.platform.service;

public record OrderPendingCreatedEvent(
        Long orderId,
        int expireMinutes
) {
}
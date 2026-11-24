package com.popq.platform.order.dto;

import com.popq.platform.order.entity.Order;
import com.popq.platform.order.entity.OrderStatus;

import java.time.LocalDateTime;

public record OrderResponse (
        Long orderId,
        String tossOrderId,
        int totalPrice,
        OrderStatus orderStatus,
        LocalDateTime orderedAt,
        LocalDateTime paymentAt,
        LocalDateTime expiresAt
) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getOrderId(),
                order.getTossOrderId(),
                order.getTotalPrice(),
                order.getOrderStatus(),
                order.getOrderedAt(),
                order.getPaymentAt(),
                order.getExpiresAt()
        );
    }
}
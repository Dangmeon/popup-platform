package com.popq.platform.order.dto;

import com.popq.platform.order.entity.Order;
import com.popq.platform.order.entity.OrderStatus;

import java.time.LocalDateTime;

public record AdminOrderResponse(
        Long orderId,
        String tossOrderId,
        Long userId,
        String userName,
        String userEmail,
        int totalPrice,
        OrderStatus status,
        LocalDateTime orderedAt,
        LocalDateTime paymentAt,
        LocalDateTime expiresAt
) {

    public static AdminOrderResponse from(Order order) {
        return new AdminOrderResponse(
                order.getOrderId(),
                order.getTossOrderId(),
                order.getUser().getId(),
                order.getUser().getName(),
                order.getUser().getUsername(),
                order.getTotalPrice(),
                order.getOrderStatus(),
                order.getOrderedAt(),
                order.getPaymentAt(),
                order.getExpiresAt()
        );
    }
}
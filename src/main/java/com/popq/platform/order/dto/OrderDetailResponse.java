package com.popq.platform.order.dto;

import com.popq.platform.order.entity.Order;
import com.popq.platform.order.entity.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailResponse(
        Long orderId,
        int totalPrice,
        OrderStatus orderStatus,
        LocalDateTime orderedAt,
        LocalDateTime paymentAt,
        LocalDateTime expiresAt,
        List<OrderItemResponse> items
) {

    public static OrderDetailResponse from(Order order) {
        List<OrderItemResponse> items = order.getOrderItems().stream()
                .map(OrderItemResponse::from)
                .toList();

        return new OrderDetailResponse(
                order.getOrderId(),
                order.getTotalPrice(),
                order.getOrderStatus(),
                order.getOrderedAt(),
                order.getPaymentAt(),
                order.getExpiresAt(),
                items
        );
    }
}
package com.popq.platform.order.dto;

import com.popq.platform.order.entity.Order;
import com.popq.platform.order.entity.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public record AdminOrderDetailResponse(
        Long orderId,
        String tossOrderId,

        Long userId,
        String userName,
        String userEmail,

        int totalPrice,
        OrderStatus status,

        LocalDateTime orderedAt,
        LocalDateTime paymentAt,

        String receiverName,
        String receiverPhone,
        String deliveryAddress,
        String deliveryAddressDetail,
        String deliveryMemo,

        List<AdminOrderItemResponse> items
) {

    public static AdminOrderDetailResponse from(Order order) {

        List<AdminOrderItemResponse> items = order.getOrderItems().stream()
                .map(AdminOrderItemResponse::from)
                .toList();

        return new AdminOrderDetailResponse(
                order.getOrderId(),
                order.getTossOrderId(),
                order.getUser().getId(),
                order.getUser().getName(),
                order.getUser().getUsername(),
                order.getTotalPrice(),
                order.getOrderStatus(),
                order.getOrderedAt(),
                order.getPaymentAt(),
                order.getReceiverName(),
                order.getReceiverPhone(),
                order.getDeliveryAddress(),
                order.getDeliveryAddressDetail(),
                order.getDeliveryMemo(),
                items
        );
    }
}
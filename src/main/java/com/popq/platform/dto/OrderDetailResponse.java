package com.popq.platform.dto;

import com.popq.platform.entity.Order;
import com.popq.platform.entity.OrderStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class OrderDetailResponse {

    private Long orderId;
    private int totalPrice;
    private OrderStatus orderStatus;

    private LocalDateTime orderedAt;
    private LocalDateTime paymentAt;
    private LocalDateTime expiresAt;

    private List<OrderItemResponse> items;

    public static OrderDetailResponse from(Order order) {
        OrderDetailResponse response = new OrderDetailResponse();

        response.orderId = order.getOrderId();
        response.totalPrice = order.getTotalPrice();
        response.orderStatus = order.getOrderStatus();
        response.orderedAt = order.getOrderedAt();
        response.paymentAt = order.getPaymentAt();
        response.expiresAt = order.getExpiresAt();

        response.items = order.getOrderItems().stream()
                .map(OrderItemResponse::from)
                .toList();

        return response;
    }
}
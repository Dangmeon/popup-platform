package com.popq.platform.dto;

import com.popq.platform.entity.Order;
import com.popq.platform.entity.OrderStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class OrderResponse {

    private Long orderId;
    private int totalPrice;
    private OrderStatus orderStatus;
    private LocalDateTime orderedAt;
    private LocalDateTime paymentAt;
    private LocalDateTime expiresAt;

    public static OrderResponse from(Order order) {
        OrderResponse response = new OrderResponse();

        response.orderId = order.getOrderId();
        response.totalPrice = order.getTotalPrice();
        response.orderStatus = order.getOrderStatus();
        response.orderedAt = order.getOrderedAt();
        response.paymentAt = order.getPaymentAt();
        response.expiresAt = order.getExpiresAt();

        return response;
    }
}
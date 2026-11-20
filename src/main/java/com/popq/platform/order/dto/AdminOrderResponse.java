package com.popq.platform.order.dto;

import com.popq.platform.order.entity.Order;
import com.popq.platform.order.entity.OrderStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class AdminOrderResponse {

    private Long orderId;
    private String tossOrderId;

    private Long userId;
    private String userName;
    private String userEmail;

    private int totalPrice;
    private OrderStatus status;

    private LocalDateTime orderedAt;
    private LocalDateTime paymentAt;
    private LocalDateTime expiresAt;

    public static AdminOrderResponse from(Order order) {
        AdminOrderResponse response = new AdminOrderResponse();

        response.orderId = order.getOrderId();
        response.tossOrderId = order.getTossOrderId();

        response.userId = order.getUser().getId();
        response.userName = order.getUser().getName();
        response.userEmail = order.getUser().getUsername();

        response.totalPrice = order.getTotalPrice();
        response.status = order.getOrderStatus();

        response.orderedAt = order.getOrderedAt();
        response.paymentAt = order.getPaymentAt();
        response.expiresAt = order.getExpiresAt();

        return response;
    }
}

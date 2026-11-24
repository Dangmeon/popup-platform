package com.popq.platform.order.dto;

import com.popq.platform.order.entity.Order;
import com.popq.platform.order.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class AdminOrderDetailResponse {

    private Long orderId;
    private String tossOrderId;

    private Long userId;
    private String userName;
    private String userEmail;

    private int totalPrice;
    private OrderStatus status;

    private LocalDateTime orderedAt;
    private LocalDateTime paymentAt;

    private String receiverName;
    private String receiverPhone;
    private String deliveryAddress;
    private String deliveryMemo;

    private List<AdminOrderItemResponse> items;

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
                order.getDeliveryMemo(),

                items
        );
    }
}

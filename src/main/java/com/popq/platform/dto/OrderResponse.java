package com.popq.platform.dto;

import com.popq.platform.entity.OrderStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class OrderResponse {

    private Long orderId;
    private Long totalPrice;
    private OrderStatus orderStatus;

    private LocalDateTime orderedAt;
    private LocalDateTime paymentAt;
    private LocalDateTime expiresAt;

    private List<OrderItemResponse> items;
}

package com.popq.platform.order.dto;

import com.popq.platform.order.entity.OrderItem;

public record OrderItemResponse (

    Long orderItemId,
    Long prdId,
    String productName,
    Integer quantity,
    int price

){
    public static OrderItemResponse from(OrderItem orderItem) {
        return new OrderItemResponse(
                orderItem.getOrderItemId(),
                orderItem.getProduct().getPrdId(),
                orderItem.getProduct().getProductName(),
                orderItem.getQuantity(),
                orderItem.getPrice()
        );
    }
}

package com.popq.platform.order.dto;

import com.popq.platform.order.entity.OrderItem;

public record AdminOrderItemResponse(
        Long prdId,
        String productName,
        String url,
        Integer quantity,
        int unitPrice
) {

    public static AdminOrderItemResponse from(OrderItem orderItem) {
        return new AdminOrderItemResponse(
                orderItem.getProduct().getPrdId(),
                orderItem.getProduct().getProductName(),
                orderItem.getProduct().getProductImg(),
                orderItem.getQuantity(),
                orderItem.getUnitPrice()
        );
    }
}
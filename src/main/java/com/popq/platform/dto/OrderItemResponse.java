package com.popq.platform.dto;

import com.popq.platform.entity.OrderItem;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OrderItemResponse {

    private Long orderItemId;
    private Long prdId;
    private  String productName;
    private Integer quantity;
    private int price;

    public static OrderItemResponse from(OrderItem orderItem) {
        OrderItemResponse response = new OrderItemResponse();

        response.orderItemId = orderItem.getOrderItemId();
        response.prdId = orderItem.getProduct().getPrdId();
        response.productName = orderItem.getProduct().getProductName();
        response.quantity = orderItem.getQuantity();
        response.price = orderItem.getPrice();

        return response;
    }
}

package com.popq.platform.dto;

import com.popq.platform.entity.OrderItem;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OrderItemResponse {

    private Long orderItemId;
    private Long productId;
    private  String productName;
    private Integer quantity;
    private int price;

    public static OrderItemResponse from(OrderItem orderItem) {
        OrderItemResponse response = new OrderItemResponse();

        response.productId = orderItem.getProduct().getPrdId();
        response.quantity = orderItem.getQuantity();
        response.price = orderItem.getPrice();

        return response;
    }
}

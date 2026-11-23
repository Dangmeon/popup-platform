package com.popq.platform.order.dto;

import com.popq.platform.order.entity.OrderItem;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AdminOrderItemResponse {

    private Long prdId;
    private String productName;
    private String url;
    private Integer quantity;
    private int unitPrice;


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

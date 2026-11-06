package com.popq.platform.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OrderItemResponse {

    private Long orderItemId;
    private Long productId;
    private  String productName;
    private Integer quantity;
    private Long price;
}

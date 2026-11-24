package com.popq.platform.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class OrderCreateRequest {

    @NotEmpty(message = "주문할 상품이 없습니다.")
    @Valid
    private List<OrderItemRequest> items;

    private Long userCouponId;

    private String receiverName;
    private String receiverPhone;
    private String deliveryAddress;
    private String deliveryAddressDetail;
    private String deliveryMemo;
}

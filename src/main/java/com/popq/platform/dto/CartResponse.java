package com.popq.platform.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CartResponse {

    // 장바구니 정보
    private Long cartId;
    private Long prdId;
    private Integer quantity;
    private LocalDateTime addedAt;

    // 상품 정보
    private Long popId;
    private String productName;
    private String productImg;
    private Integer price;
    private Integer stock;
    private Integer maxQty;
    private String saleStatus;

    // 구매 정보
    private Long purchasedQuantity;
    private Long itemTotalPrice;
    private boolean purchasable;
    private String unavailableReason;
}
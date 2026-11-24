package com.popq.platform.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;


@Getter
@AllArgsConstructor
@Builder
public class RecentOrderResponse { // 대시보드 : 최근 주문
    private Long orderId;
    private String productName; // 대표 상품명 (여러 개면 "상품명 외 n건")
    private int price;          // ADMIN : 주문 총액 / MANAGER : 담당 팝업 상품 금액만
    private String status;      // PAID / CANCELLED / EXPIRED / FAILED
}

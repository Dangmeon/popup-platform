package com.popq.platform.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class ProductRequest {
    private Long popId;
    private String productName;
    private Integer price;
    private Integer stock;            // 등록할 때만 사용 (수정 시 무시)
    private LocalDateTime saleStart;  // 예) "2026-11-01T10:00:00"
    private LocalDateTime saleEnd;
    private String category;
    private String productImg;
    private String productDesc;
    private Integer maxQty;           // 1인 구매 제한, 비우면 1개
}

package com.popq.platform.dto;

import com.popq.platform.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Builder
public class ProductResponse {
    private Long prdId;
    private Long popId;
    private String productName;
    private Integer price;
    private Integer stock;
    private LocalDateTime saleStart;
    private LocalDateTime saleEnd;
    private String category;
    private String productImg;
    private String productDesc;
    private Integer maxQty;
    private String saleStatus; // UPCOMING 판매예정 / ON_SALE 판매중 / SOLD_OUT 품절 / ENDED 판매종료 (계산값)

    // Entity -> Response 변환시 사용
    public static ProductResponse toDto(Product entity) {
        return ProductResponse.builder()
                .prdId(entity.getPrdId())
                .popId(entity.getPopupStore().getPopId())
                .productName(entity.getProductName())
                .price(entity.getPrice())
                .stock(entity.getStock())
                .saleStart(entity.getSaleStart())
                .saleEnd(entity.getSaleEnd())
                .category(entity.getCategory())
                .productImg(entity.getProductImg())
                .productDesc(entity.getProductDesc())
                .maxQty(entity.getMaxQty())
                .saleStatus(getSaleStatus(entity))
                .build();
    }

    // 지금 시간 기준 판매 상태 계산
    private static String getSaleStatus(Product entity) {
        LocalDateTime now = LocalDateTime.now();
        if (entity.getSaleStart() != null && now.isBefore(entity.getSaleStart())) {
            return "UPCOMING";
        }
        if (entity.getSaleEnd() != null && now.isAfter(entity.getSaleEnd())) {
            return "ENDED";
        }
        return entity.getStock() > 0 ? "ON_SALE" : "SOLD_OUT";
    }
}

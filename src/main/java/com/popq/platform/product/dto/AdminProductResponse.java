package com.popq.platform.product.dto;

import com.popq.platform.product.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// 관리자 상품 목록 한 줄 : 상품 정보 + 어느 팝업의 상품인지 (팝업명, 팝업 운영 상태)
@Getter
@AllArgsConstructor
@Builder
public class AdminProductResponse {
    private Long prdId;
    private Long popId;
    private String popupTitle;
    private String popupOpStatus; // OPEN / PAUSED / CANCELLED
    private String productName;
    private Integer price;
    private Integer stock;
    private LocalDateTime saleStart;
    private LocalDateTime saleEnd;
    private String productImg;
    private Integer maxQty;
    private String saleStatus; // UPCOMING 판매예정 / ON_SALE 판매중 / SOLD_OUT 품절 / ENDED 판매종료 (계산값)

    // Entity -> Response 변환 (판매 상태는 ProductResponse와 같은 계산 사용)
    public static AdminProductResponse toDto(Product entity) {
        return AdminProductResponse.builder()
                .prdId(entity.getPrdId())
                .popId(entity.getPopupStore().getPopId())
                .popupTitle(entity.getPopupStore().getTitle())
                .popupOpStatus(entity.getPopupStore().getOpStatus())
                .productName(entity.getProductName())
                .price(entity.getPrice())
                .stock(entity.getStock())
                .saleStart(entity.getSaleStart())
                .saleEnd(entity.getSaleEnd())
                .productImg(entity.getProductImg())
                .maxQty(entity.getMaxQty())
                .saleStatus(ProductResponse.toDto(entity).getSaleStatus())
                .build();
    }
}

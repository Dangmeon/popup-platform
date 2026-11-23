package com.popq.platform.dto;

import com.popq.platform.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;


@Getter
@AllArgsConstructor
@Builder
public class LowStockProductResponse { // 대시보드 : 재고 부족 상품
    private Long prdId;
    private String productName;
    private int stock;
    private String popupTitle;

    public static LowStockProductResponse toDto(Product product) {
        return LowStockProductResponse.builder()
                .prdId(product.getPrdId())
                .productName(product.getProductName())
                .stock(product.getStock() == null ? 0 : product.getStock())
                .popupTitle(product.getPopupStore() == null ? null : product.getPopupStore().getTitle())
                .build();
    }
}

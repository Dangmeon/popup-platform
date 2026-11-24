package com.popq.platform.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SalesSummaryResponse {

    private Long totalSales;
    private Long totalOrderCount;
    private Long totalQuantity;
}

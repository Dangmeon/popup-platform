package com.popq.platform.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PopupSalesResponse {

    private Long popId;
    private String title;
    private Long totalSales;
    private Long totalOrderCount;
    private Long totalQuantity;
}

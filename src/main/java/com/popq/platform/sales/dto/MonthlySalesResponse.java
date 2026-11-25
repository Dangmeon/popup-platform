package com.popq.platform.sales.dto;

public record MonthlySalesResponse(

        String  month,
        long totalSales
) {
}

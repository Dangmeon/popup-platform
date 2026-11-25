package com.popq.platform.sales.dto;

import java.time.LocalDate;

public record WeekSalesResponse(

        LocalDate weekStart,
        LocalDate weekEnd,
        Long totalSales
) {
}

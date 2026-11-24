package com.popq.platform.sales.repository;

public interface SalesSummaryProjection {

    Long getTotalSales();

    Long getTotalOrderCount();

    Long getTotalQuantity();
}

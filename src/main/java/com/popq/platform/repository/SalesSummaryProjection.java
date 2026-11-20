package com.popq.platform.repository;

public interface SalesSummaryProjection {

    Long getTotalSales();

    Long getTotalOrderCount();

    Long getTotalQuantity();
}

package com.popq.platform.sales.repository;

public interface MonthlySalesProjection {

    int getYear();
    int getMonth();
    Long getTotalSales();
}

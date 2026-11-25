package com.popq.platform.sales.repository;

public interface WeeklySalesProjection {

    int getYear();
    int getMonth();
    int getDay();
    Long getTotalSales();
}

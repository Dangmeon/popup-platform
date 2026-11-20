package com.popq.platform.order.repository;

import com.popq.platform.order.entity.Order;
import com.popq.platform.order.entity.OrderItem;
import com.popq.platform.order.entity.OrderStatus;
import com.popq.platform.repository.SalesSummaryProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("""
        SELECT COALESCE(SUM(oi.quantity), 0)
        FROM OrderItem oi
        JOIN oi.order o
        WHERE o.user.id = :memberId
            AND oi.product.prdId = :prdId
            AND o.orderStatus IN :statuses
    """)
    int sumPurchasedQuantity(
            @Param("memberId") Long memberId,
            @Param("prdId") Long prdId,
            @Param("statuses") List<OrderStatus> statuses
    );

    @Query("""
        SELECT
            COALESCE(SUM(oi.price), 0) AS totalSales,
            COUNT(DISTINCT o.orderId) AS totalOrderCount,
            COALESCE(SUM(oi.quantity), 0) AS totalQuantity
        FROM OrderItem oi
        JOIN oi.order o
        WHERE o.orderStatus = :status
    """)
    SalesSummaryProjection findSalesSummary(
            @Param("status") OrderStatus status
    );

    @Query("""
        SELECT
            COALESCE(SUM(oi.price), 0) AS totalSales,
            COUNT(DISTINCT o.orderId) AS totalOrderCount,
            COALESCE(SUM(oi.quantity), 0) AS totalQuantity
        FROM OrderItem oi
        JOIN oi.order o
        JOIN oi.product p
        WHERE o.orderStatus = :status
            AND p.popupStore.popId = :popId
    """)
    SalesSummaryProjection findSalesSummaryByPopup(
            @Param("popId") Long popId,
            @Param("status") OrderStatus status
    );

}

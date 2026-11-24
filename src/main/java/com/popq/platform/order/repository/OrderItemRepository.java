package com.popq.platform.order.repository;

import com.popq.platform.order.entity.OrderItem;
import com.popq.platform.order.entity.OrderStatus;
import com.popq.platform.sales.repository.SalesSummaryProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * 전체 팝업스토어 기간별 확정 매출 집계 (전월 매출 / 당월 현재까지 매출 산출용)
     *
     * 1. 날짜 기준 COALESCE(o.paymentAt, o.orderedAt):
     *    - 결제 완료 시각(paymentAt)을 최우선 기준으로 삼되, 테스트/더미 데이터 등에서 paymentAt이 null인 경우
     *      주문 생성 시각(orderedAt)으로 대체하여 집계가 누락되지 않도록 방어
     * 2. 반개구간 범위 조건 (>= startAt AND < endAt):
     *    - 예: 10월 매출 조회 시 [2026-10-01 00:00:00 이상 ~ 2026-11-01 00:00:00 미만]으로 조회
     *    - BETWEEN (<= 23:59:59) 대신 '< 다음날 00:00:00'을 쓰면 23:59:59.500 같은 나노초 단위 경계값 누락 방어
     */
    @Query("""
        SELECT
            COALESCE(SUM(oi.price), 0) AS totalSales,
            COUNT(DISTINCT o.orderId) AS totalOrderCount,
            COALESCE(SUM(oi.quantity), 0) AS totalQuantity
        FROM OrderItem oi
        JOIN oi.order o
        WHERE o.orderStatus = :status
          AND COALESCE(o.paymentAt, o.orderedAt) >= :startAt
          AND COALESCE(o.paymentAt, o.orderedAt) < :endAt
    """)
    SalesSummaryProjection findSalesSummaryBetween(
            @Param("status") OrderStatus status,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    /**
     * 특정 팝업스토어(popId)의 기간별 확정 매출 집계
     * - OrderItem -> Product -> PopupStore 경로로 조인하여 해당 팝업 소속 굿즈 매출만 필터링
     */
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
          AND COALESCE(o.paymentAt, o.orderedAt) >= :startAt
          AND COALESCE(o.paymentAt, o.orderedAt) < :endAt
    """)
    SalesSummaryProjection findSalesSummaryByPopupBetween(
            @Param("popId") Long popId,
            @Param("status") OrderStatus status,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

}

package com.popq.platform.order.repository;

import com.popq.platform.order.entity.Order;
import com.popq.platform.order.entity.OrderItem;
import com.popq.platform.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("""
        SELECT o
        FROM Order o
        WHERE o.orderId = :orderId
          AND o.user.id = :memberId
        """)
    Optional<Order> findByOrderIdAndMemberId(
            @Param("orderId") Long orderId,
            @Param("memberId") Long memberId
    );

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
}

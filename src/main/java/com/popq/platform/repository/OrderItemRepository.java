package com.popq.platform.repository;

import com.popq.platform.entity.Order;
import com.popq.platform.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}

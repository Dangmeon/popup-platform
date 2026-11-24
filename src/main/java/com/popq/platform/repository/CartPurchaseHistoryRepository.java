package com.popq.platform.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class CartPurchaseHistoryRepository {

    private final JdbcTemplate jdbcTemplate;

    // 회원의 상품별 누적 주문 수량
    public Map<Long, Long> findPurchasedQuantities(Long memberId) {
        String sql = """
                SELECT oi.prd_id, SUM(oi.quantity) AS purchased_quantity
                FROM orders o
                JOIN order_item oi ON oi.order_id = o.order_id
                WHERE o.member_id = ?
                  AND o.order_status NOT IN ('CANCELLED', 'EXPIRED')
                GROUP BY oi.prd_id
                """;

        return jdbcTemplate.query(sql, resultSet -> {
            Map<Long, Long> quantities = new HashMap<>();

            while (resultSet.next()) {
                quantities.put(
                        resultSet.getLong("prd_id"),
                        resultSet.getLong("purchased_quantity")
                );
            }

            return quantities;
        }, memberId);
    }
}

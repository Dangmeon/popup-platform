package com.popq.platform.order.service;

import com.popq.platform.order.entity.OrderStatus;
import com.popq.platform.order.redis.OrderExpirationService;
import com.popq.platform.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {

    private static final long FALLBACK_INTERVAL = 60 * 1000L;

    private final OrderRepository orderRepository;
    private final OrderExpirationService orderExpirationService;

    // Redis 만료 이벤트 누락에 대비한 주문 만료 보완 처리
    @Scheduled(fixedDelay = FALLBACK_INTERVAL)
    public void expirePendingOrders() {
        List<Long> expiredOrderIds = orderRepository.findExpiredOrderIds(OrderStatus.PENDING, LocalDateTime.now());

        for (Long orderId : expiredOrderIds) {
            try {
                orderExpirationService.expireOrder(orderId);
            } catch (Exception e) {
                log.error("주문 만료 처리 실패: orderId={}", orderId, e);
            }
        }
    }
}
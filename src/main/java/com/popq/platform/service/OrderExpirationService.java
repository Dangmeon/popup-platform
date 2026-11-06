package com.popq.platform.service;

import com.popq.platform.repository.OrderItemRepository;
import com.popq.platform.repository.OrderRepository;
import com.popq.platform.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderExpirationService {

    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final ProductRepository productRepo;

    // 결제 대기 주문 만료 처리
    @Transactional
    public void expireOrder(Long orderId) {
        // Order 비관적 락 조회
        // 주문 상태가 PENDING인지 확인
        // expiresAt이 현재 시각을 지났는지 확인
        // OrderItem 목록 조회
        // 상품별 확보 재고 복구
        // OrderStatus → EXPIRED 변경
        // 중복 만료 시 재고가 다시 복구되지 않도록 처리
    }
}

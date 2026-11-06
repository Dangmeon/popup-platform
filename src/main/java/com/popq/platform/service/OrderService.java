package com.popq.platform.service;

import com.popq.platform.dto.OrderCreateRequest;
import com.popq.platform.dto.OrderResponse;
import com.popq.platform.repository.OrderItemRepository;
import com.popq.platform.repository.OrderRepository;
import com.popq.platform.repository.ProductRepository;
import com.popq.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    // 주문 생성
    @Transactional
    public OrderResponse createOrder(Long memberId, OrderCreateRequest request) {
        // 로그인 회원 조회
        // 주문 상품 및 수량 검증
        // 상품 판매 기간 및 구매 제한 확인
        // 재고 확인 및 차감
        // 총 주문 금액 계산
        // Order 생성 (PENDING, expiresAt 설정)
        // OrderItem 저장
        // DB 커밋 이후 Redis TTL Key 생성 이벤트 발행
        // 주문 응답 반환

        return null;
    }

    // 내 주문 목록 조회
    public List<OrderResponse> getMyOrders(Long memberId) {
        // 회원별 주문 조회
        // 주문 응답 DTO 변환

        return List.of();
    }

    // 주문 상세 조회
    public OrderResponse getOrderDetail(Long memberId, Long orderId) {
        // 주문 존재 여부 확인
        // 본인 주문 여부 확인
        // 주문 상품 조회 및 DTO 변환

        return null;
    }

    // 주문 취소
    @Transactional
    public OrderResponse cancelOrder(Long memberId, Long orderId) {
        // 주문 락 조회
        // 본인 주문 여부 확인
        // 취소 가능한 주문 상태인지 확인
        // PENDING 주문 취소 및 재고 복구
        // PAID 주문은 PaymentService의 결제 취소 처리와 연계
        // 주문 상태 CANCELED 변경
        // Redis TTL Key 삭제
        // 주문 응답 반환

        return null;
    }
}

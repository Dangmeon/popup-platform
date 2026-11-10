package com.popq.platform.service;

import com.popq.platform.dto.OrderCreateRequest;
import com.popq.platform.dto.OrderDetailResponse;
import com.popq.platform.dto.OrderItemRequest;
import com.popq.platform.dto.OrderResponse;
import com.popq.platform.entity.*;
import com.popq.platform.repository.OrderItemRepository;
import com.popq.platform.repository.OrderRepository;
import com.popq.platform.repository.ProductRepository;
import com.popq.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private static final int HOLD_EXPIRE_MINUTES = 10;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    // 주문 생성
    @Transactional
    public OrderResponse createOrder(Long memberId, OrderCreateRequest request) {

        // 로그인 회원 조회
        User user = userRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원이 없습니다."));

        int totalPrice = 0;
        LocalDateTime now = LocalDateTime.now();

        // 조회한 상품 재사용
        Map<Long, Product> productMap = new HashMap<>();

        // 1. 상품 검증 + 재고 차감 + 총 금액 계산
        for (OrderItemRequest item : request.getItems()) {
            // 수량 검증
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new IllegalArgumentException("상품은 한 개 이상이어야 합니다.");
            }

            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("상품이 없습니다."));

            productMap.put(item.getProductId(), product);

            // 판매 기간 검증
            if(product.getSaleStart() != null && now.isBefore(product.getSaleStart())) {
                throw new IllegalArgumentException("아직 판매 시작 전인 상품입니다.");
            }
            if(product.getSaleEnd() != null && now.isAfter(product.getSaleEnd())) {
                throw new IllegalArgumentException("판매가 종료된 상품입니다.");
            }

            // 1인 구매 제한 검증
            int purchaseQuantity = orderItemRepository.sumPurchasedQuantity(
                    memberId, item.getProductId(), List.of(OrderStatus.PENDING, OrderStatus.PAID));

            if(purchaseQuantity + item.getQuantity() > product.getMaxQty()){
                throw new IllegalArgumentException(
                        "해당 상품은 1인당 최대 " + product.getMaxQty() + "개까지 구매할 수 있습니다.");
            }

            // 재고 차감
            int updatedRows = productRepository.decreaseStock(
                    item.getProductId(), item.getQuantity()
            );

            if (updatedRows == 0) {
                throw new IllegalArgumentException("재고가 부족합니다.");
            }

            // 총 주문 금액
            int price = product.getPrice() * item.getQuantity();
            totalPrice += price;
        }

        // 2. 주문 생성
        LocalDateTime expiresAt = now.plusMinutes(HOLD_EXPIRE_MINUTES);

        Order order = Order.createOrder(
                user,
                totalPrice,
                expiresAt
        );

        // 3. OrderItem 생성 + Order에 추가
        for (OrderItemRequest item : request.getItems()) {

            Product product = productMap.get(item.getProductId());

            int price = product.getPrice() * item.getQuantity();

            OrderItem orderItem = OrderItem.createOrderItem(
                    product,
                    item.getQuantity(),
                    price
            );

            order.addOrderItem(orderItem);
        }

        // 4. Order 저장
        // cascade로 OrderItem도 같이 저장
        orderRepository.save(order);

        // 5. TODO Redis TTL 이벤트 발행

        // 6. 응답 반환
        return OrderResponse.from(order);
    }

    // 내 주문 목록 조회
    public List<OrderResponse> getMyOrders(Long memberId) {
        // 회원별 주문 조회
        userRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원이 아닙니다."));

        List<Order> orders = orderRepository.findByMemberId(memberId);

        // 주문 응답 DTO 변환
        return orders.stream()
                .map(OrderResponse::from)
                .toList();
    }

    // 주문 상세 조회
    public OrderDetailResponse getOrderDetail(Long memberId, Long orderId) {
        // 주문 존재 여부 확인
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문한 내역이 없습니다."));

        // 본인 주문 여부 확인
        if(!order.getUser().getId().equals(memberId)) {
            throw new IllegalArgumentException("본인의 주문만 조회할 수 있습니다.");
        }

        // 주문 상품 조회 및 DTO 변환
        return OrderDetailResponse.from(order);
    }

    // 주문 취소
    @Transactional
    public OrderResponse cancelOrder(Long memberId, Long orderId) {
        // 주문 락 조회
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문이 없습니다."));

        // 본인 주문 여부 확인
        if (!order.getUser().getId().equals(memberId)) {
            throw new IllegalArgumentException("본인의 주문만 취소할 수 있습니다.");
        }

        // 취소 가능한 주문 상태인지 확인
        if(order.getOrderStatus() == OrderStatus.CANCELED){
            throw new IllegalArgumentException("이미 취소된 주문입니다.");
        }
        if (order.getOrderStatus() == OrderStatus.EXPIRED) {
            throw new IllegalArgumentException("이미 만료된 주문입니다.");
        }

        // PENDING 주문 취소 및 재고 복구
        if(order.getOrderStatus() == OrderStatus.PENDING){

            for(OrderItem orderItem : order.getOrderItems()) {
                int updatedRows =
                        productRepository.increaseStock(
                            orderItem.getProduct().getPrdId(), orderItem.getQuantity());

                if (updatedRows == 0) {
                    throw new IllegalStateException("재고 복구 중 오류가 발생했습니다.");
                }
            }

            // 주문 상태 CANCELED 변경
            order.cancel();

            // TODO Redis TTL Key 삭제

            // 주문 응답 반환
            return OrderResponse.from(order);
        }

        // PAID 주문은 PaymentService의 결제 취소 처리와 연계
        if(order.getOrderStatus() == OrderStatus.PAID){

            throw new IllegalStateException("결제 완료 주문은 결제 취소 연동이 필요합니다.");
        }

        throw new IllegalArgumentException("취소할 수 없는 주문 상태입니다.");
    }
}

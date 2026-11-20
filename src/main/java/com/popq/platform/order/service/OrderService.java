package com.popq.platform.order.service;

import com.popq.platform.order.dto.OrderCreateRequest;
import com.popq.platform.order.dto.OrderDetailResponse;
import com.popq.platform.order.dto.OrderItemRequest;
import com.popq.platform.order.dto.OrderResponse;
import com.popq.platform.entity.*;
import com.popq.platform.order.entity.Order;
import com.popq.platform.order.entity.OrderItem;
import com.popq.platform.order.entity.OrderStatus;
import com.popq.platform.order.exception.OrderErrorCode;
import com.popq.platform.order.exception.OrderException;
import com.popq.platform.exception.ProductErrorCode;
import com.popq.platform.exception.ProductException;
import com.popq.platform.order.event.OrderPendingCreatedEvent;
import com.popq.platform.redis.RedisExpirationKeyService;
import com.popq.platform.order.repository.OrderItemRepository;
import com.popq.platform.order.repository.OrderRepository;
import com.popq.platform.repository.ProductRepository;
import com.popq.platform.repository.UserRepository;
import com.popq.platform.payment.service.PaymentService;
import com.popq.platform.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private static final int ORDER_EXPIRE_MINUTES = 10;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RedisExpirationKeyService redisExpirationKeyService;
    private final PaymentService paymentService;
    private final CartService cartService;

    // 주문 생성
    @Transactional
    public OrderResponse createOrder(String username, OrderCreateRequest request) {

        // 로그인 회원 조회
        User user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new IllegalArgumentException("회원이 없습니다."));

        int totalPrice = 0;
        LocalDateTime now = LocalDateTime.now();

        // 조회한 상품 재사용
        Map<Long, Product> productMap = new HashMap<>();

        // 1. 상품 검증 + 재고 차감 + 총 금액 계산
        for (OrderItemRequest item : request.getItems()) {
            // 수량 검증
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new OrderException(OrderErrorCode.INVALID_QUANTITY);
            }

            Product product = productRepository.findById(item.getPrdId())
                    .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND));

            if(!"OPEN".equals(product.getPopupStore().getOpStatus())){
                throw new OrderException(OrderErrorCode.POPUP_NOT_OPEN);
            }

            productMap.put(item.getPrdId(), product);

            // 판매 기간 검증
            if (product.getSaleStart() != null && now.isBefore(product.getSaleStart())) {
                throw new OrderException(OrderErrorCode.SALE_NOT_STARTED);
            }

            if (product.getSaleEnd() != null && now.isAfter(product.getSaleEnd())) {
                throw new OrderException(OrderErrorCode.SALE_ENDED);
            }

            // 1인 구매 제한 검증
            int purchaseQuantity = orderItemRepository.sumPurchasedQuantity(
                    user.getId(), item.getPrdId(), List.of(OrderStatus.PENDING, OrderStatus.PAID));

            if (purchaseQuantity + item.getQuantity() > product.getMaxQty()) {
                throw new OrderException(
                        OrderErrorCode.PURCHASE_LIMIT_EXCEEDED,
                        "해당 상품은 1인당 최대 " + product.getMaxQty() + "개까지 구매할 수 있습니다.");
            }

            // 재고 차감
            int updatedRows = productRepository.decreaseStock(
                    item.getPrdId(), item.getQuantity()
            );

            if (updatedRows == 0) {
                throw new OrderException(OrderErrorCode.INSUFFICIENT_STOCK);
            }

            // 총 주문 금액
            int price = product.getPrice() * item.getQuantity();
            totalPrice += price;
        }

        // 2. 주문 생성
        LocalDateTime expiresAt = now.plusMinutes(ORDER_EXPIRE_MINUTES);

        Order order = Order.createOrder(
                user,
                totalPrice,
                expiresAt
        );

        // 3. OrderItem 생성 + Order에 추가
        for (OrderItemRequest item : request.getItems()) {

            Product product = productMap.get(item.getPrdId());

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
        Order savedOrder =  orderRepository.save(order);

        List<Long> orderedPrdIds = request.getItems().stream()
                        .map(OrderItemRequest::getPrdId)
                        .toList();

        cartService.removeItemsByProductIds(username, orderedPrdIds);

        // 5. TODO Redis TTL 이벤트 발행
        eventPublisher.publishEvent(
                new OrderPendingCreatedEvent(
                        savedOrder.getOrderId(),
                        ORDER_EXPIRE_MINUTES
                )
        );

        // 6. 응답 반환
        return OrderResponse.from(savedOrder);
    }

    // 내 주문 목록 조회
    public List<OrderResponse> getMyOrders(String username) {
        // 회원별 주문 조회
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("회원이 아닙니다."));

        List<Order> orders = orderRepository.findByMemberId(user.getId());

        // 주문 응답 DTO 변환
        return orders.stream()
                .map(OrderResponse::from)
                .toList();
    }

    // 주문 상세 조회
    public OrderDetailResponse getOrderDetail(String username, Long orderId) {
        // 주문 존재 여부 확인
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        // 본인 주문 여부 확인
        if (!order.getUser().getUsername().equals(username)) {
            throw new OrderException(OrderErrorCode.ORDER_ACCESS_DENIED);
        }

        // 주문 상품 조회 및 DTO 변환
        return OrderDetailResponse.from(order);
    }

    // 주문 취소
    @Transactional
    public OrderResponse cancelOrder(String username, Long orderId) {
        // 주문 락 조회
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        // 본인 주문 여부 확인
        if (!order.getUser().getUsername().equals(username)) {
            throw new OrderException(OrderErrorCode.ORDER_ACCESS_DENIED);
        }

        // 취소 가능한 주문 상태인지 확인
        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            throw new OrderException(OrderErrorCode.ORDER_ALREADY_CANCELLED);
        }

        if (order.getOrderStatus() == OrderStatus.EXPIRED) {
            throw new OrderException(OrderErrorCode.ORDER_EXPIRED);
        }

        // PENDING 주문 취소 및 재고 복구
        if(order.getOrderStatus() == OrderStatus.PENDING){

            restoreStock(order);

            // 주문 상태 CANCELED 변경
            order.cancel();

            // TODO Redis TTL Key 삭제
            redisExpirationKeyService.delete("order:pending:" + orderId);

            // 주문 응답 반환
            return OrderResponse.from(order);
        }

        // PAID 주문은 PaymentService의 결제 취소 처리와 연계
        if(order.getOrderStatus() == OrderStatus.PAID){

            paymentService.cancelPayment(order);

            restoreStock(order);

            order.cancel();

            return OrderResponse.from(order);
        }

        throw new OrderException(OrderErrorCode.INVALID_ORDER_STATUS);
    }

    private void restoreStock(Order order) {

        for (OrderItem orderItem : order.getOrderItems()) {
            int updatedRows = productRepository.increaseStock(
                    orderItem.getProduct().getPrdId(),
                    orderItem.getQuantity()
            );

            if (updatedRows == 0) {
                throw new OrderException(OrderErrorCode.STOCK_RESTORE_FAILED);
            }
        }
    }
}

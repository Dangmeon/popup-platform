package com.popq.platform.order.service;

import com.popq.platform.auth.entity.User;
import com.popq.platform.coupon.entity.UserCoupon;
import com.popq.platform.exception.AuthErrorCode;
import com.popq.platform.exception.AuthException;
import com.popq.platform.order.dto.*;
import com.popq.platform.order.entity.Order;
import com.popq.platform.order.entity.OrderItem;
import com.popq.platform.order.entity.OrderStatus;
import com.popq.platform.order.exception.OrderErrorCode;
import com.popq.platform.order.exception.OrderException;
import com.popq.platform.product.entity.Product;
import com.popq.platform.product.exception.ProductErrorCode;
import com.popq.platform.product.exception.ProductException;
import com.popq.platform.order.event.OrderPendingCreatedEvent;
import com.popq.platform.common.redis.RedisExpirationKeyService;
import com.popq.platform.order.repository.OrderItemRepository;
import com.popq.platform.order.repository.OrderRepository;
import com.popq.platform.product.repository.ProductRepository;
import com.popq.platform.auth.repository.UserRepository;
import com.popq.platform.payment.service.PaymentService;
import com.popq.platform.cart.service.CartService;
import com.popq.platform.coupon.service.CouponService;
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
    private final CouponService couponService;

    // 주문 생성
    @Transactional
    public OrderResponse createOrder(String username, OrderCreateRequest request) {

        // 로그인 회원 조회
        User user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        int totalPrice = 0;
        LocalDateTime now = LocalDateTime.now();

        // 조회한 상품 재사용
        Map<Long, Product> productMap = new HashMap<>();

        List<OrderItemRequest> items = request.getItems().stream()
                .sorted(Comparator.comparing(OrderItemRequest::getPrdId))
                .toList();

        // 1. 상품 검증 + 재고 차감 + 총 금액 계산
        for (OrderItemRequest item : items) {
            // 수량 검증
            validateQuantity(item.getQuantity());

            Product product = getProduct(item.getPrdId());

            productMap.put(item.getPrdId(), product);

            if (!"OPEN".equals(product.getPopupStore().getOpStatus())) {
                throw new OrderException(OrderErrorCode.POPUP_NOT_OPEN);
            }

            // 판매 기간 검증
            validateSalePeriod(product, now);

            // 1인 구매 제한 검증
            validatePurchaseLimit(user, product, item.getQuantity());

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

        UserCoupon userCoupon = null;

        if (request.getUserCouponId() != null) {

            userCoupon = couponService.getValidUserCoupon(username, request.getUserCouponId());

            totalPrice = couponService.calculateDiscountedPrice(
                    username,
                    request.getUserCouponId(),
                    totalPrice
            );
        }

        // 2. 주문 생성
        LocalDateTime expiresAt = now.plusMinutes(ORDER_EXPIRE_MINUTES);

        Order order = Order.createOrder(
                user,
                totalPrice,
                expiresAt,
                request.getReceiverName(),
                request.getReceiverPhone(),
                request.getDeliveryAddress(),
                request.getDeliveryAddressDetail(),
                request.getDeliveryMemo(),
                userCoupon
        );

        // 3. OrderItem 생성 + Order에 추가
        for (OrderItemRequest item : items) {

            Product product = productMap.get(item.getPrdId());

            int unitPrice = product.getPrice();
            int price = product.getPrice() * item.getQuantity();

            OrderItem orderItem = OrderItem.createOrderItem(
                    product,
                    item.getQuantity(),
                    unitPrice,
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
        User user = getUser(username);

        List<Order> orders = orderRepository.findByMemberId(user.getId());

        // 주문 응답 DTO 변환
        return orders.stream()
                .map(OrderResponse::from)
                .toList();
    }

    // 주문 상세 조회
    public OrderDetailResponse getOrderDetail(String username, Long orderId) {
        // 주문 존재 여부 확인
        Order order = getOrder(orderId);

        // 본인 주문 여부 확인
        validateOrderOwner(order, username);

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
        validateOrderOwner(order, username);

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

    public List<AdminOrderResponse> getAllOrdersAdmin(){
        return orderRepository.findAllOrders()
                .stream()
                .map(AdminOrderResponse::from)
                .toList();
    }

    public AdminOrderDetailResponse getAdminOrderDetail(Long orderId) {

        Order order = getOrder(orderId);

        return AdminOrderDetailResponse.from(order);
    }

    @Transactional(readOnly = true)
    public RecentDeliveryResponse getRecentDelivery(String username) {

        Order order = orderRepository.findTopByUserUsernameOrderByOrderedAtDesc(username)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        return RecentDeliveryResponse.from(order);
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

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
    }

    private Product getProduct(Long prdId) {
        return productRepository.findById(prdId)
                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }

    private Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

    }

    private void validateOrderOwner(Order order, String username) {

        if (!order.getUser().getUsername().equals(username)) {
            throw new OrderException(OrderErrorCode.ORDER_ACCESS_DENIED);
        }
    }

    private void validateQuantity(Integer quantity) {

        if (quantity == null || quantity <= 0) {
            throw new OrderException(OrderErrorCode.INVALID_QUANTITY);
        }
    }

    private void validateSalePeriod(Product product, LocalDateTime now) {

        if (product.getSaleStart() != null && now.isBefore(product.getSaleStart())) {
            throw new OrderException(OrderErrorCode.SALE_NOT_STARTED);
        }

        if (product.getSaleEnd() != null && now.isAfter(product.getSaleEnd())) {
            throw new OrderException(OrderErrorCode.SALE_ENDED);
        }
    }

    private void validatePurchaseLimit(User user, Product product, Integer quantity) {

        int purchaseQuantity = orderItemRepository.sumPurchasedQuantity(
                user.getId(), product.getPrdId(), List.of(OrderStatus.PENDING, OrderStatus.PAID));

        if (purchaseQuantity + quantity > product.getMaxQty()) {
            throw new OrderException(
                    OrderErrorCode.PURCHASE_LIMIT_EXCEEDED,
                    "해당 상품은 1인당 최대 " + product.getMaxQty() + "개까지 구매할 수 있습니다."
            );
        }
    }
}

package com.popq.platform.order.service;

import com.popq.platform.auth.entity.User;
import com.popq.platform.auth.repository.UserRepository;
import com.popq.platform.cart.service.CartService;
import com.popq.platform.common.redis.RedisExpirationKeyService;
import com.popq.platform.coupon.entity.UserCoupon;
import com.popq.platform.coupon.service.CouponService;
import com.popq.platform.exception.AuthErrorCode;
import com.popq.platform.exception.AuthException;
import com.popq.platform.order.dto.*;
import com.popq.platform.order.entity.Order;
import com.popq.platform.order.entity.OrderItem;
import com.popq.platform.order.entity.OrderStatus;
import com.popq.platform.order.event.OrderPendingCreatedEvent;
import com.popq.platform.order.exception.OrderErrorCode;
import com.popq.platform.order.exception.OrderException;
import com.popq.platform.order.repository.OrderItemRepository;
import com.popq.platform.order.repository.OrderRepository;
import com.popq.platform.payment.service.PaymentService;
import com.popq.platform.popup.entity.PopupStore;
import com.popq.platform.product.entity.Product;
import com.popq.platform.product.exception.ProductErrorCode;
import com.popq.platform.product.exception.ProductException;
import com.popq.platform.product.repository.ProductRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private RedisExpirationKeyService redisExpirationKeyService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private CartService cartService;

    @Mock
    private CouponService couponService;

    @InjectMocks
    private OrderService orderService;


    // 공통 테스트 데이터
    private static final String USERNAME = "testUser";
    private static final String OTHER_USERNAME = "otherUser";

    private static final Long MEMBER_ID = 1L;
    private static final Long PRD_ID = 2L;
    private static final Long ORDER_ID = 3L;
    private static final Long COUPON_ID = 4L;

    private static final int UNIT_PRICE = 10000;
    private static final int MAX_QTY = 5;

    private static final String RECEIVER_NAME = "테스트 회원";
    private static final String RECEIVER_PHONE = "01012345678";
    private static final String DELIVERY_ADDRESS = "테스트 주소";
    private static final String DELIVERY_ADDRESS_DETAIL = "101호";
    private static final String DELIVERY_MEMO = "문 앞에 놓아주세요";

    private static final List<OrderStatus> ACTIVE_STATUSES =
            List.of(
                    OrderStatus.PENDING,
                    OrderStatus.PAID
            );

    // 공통 테스트 데이터 생성 메서드

    // 실제 회원 엔티티 생성
    private User createUser() {

        User user = User.builder()
                .username(USERNAME)
                .password("testPassword")
                .name("테스트 회원")
                .phone(RECEIVER_PHONE)
                .build();

        ReflectionTestUtils.setField(
                user,
                "id",
                MEMBER_ID
        );

        return user;
    }

    // 로그인 회원 비관적 락 조회 성공 설정
    private User mockLoginUser() {

        User user = createUser();

        when(userRepository.findByUsernameForUpdate(USERNAME)).thenReturn(Optional.of(user));

        return user;
    }

    // 실제 주문 상품 요청 DTO 생성
    private OrderItemRequest createItemRequest(Long prdId, Integer quantity) {

        OrderItemRequest item = new OrderItemRequest();

        ReflectionTestUtils.setField(
                item,
                "prdId",
                prdId
        );

        ReflectionTestUtils.setField(
                item,
                "quantity",
                quantity
        );

        return item;
    }


    // 실제 주문 요청 DTO 생성
    private OrderCreateRequest createOrderRequest(List<OrderItemRequest> items, Long userCouponId) {

        OrderCreateRequest request = new OrderCreateRequest();

        ReflectionTestUtils.setField(
                request,
                "items",
                items
        );

        ReflectionTestUtils.setField(
                request,
                "userCouponId",
                userCouponId
        );

        ReflectionTestUtils.setField(
                request,
                "receiverName",
                RECEIVER_NAME
        );

        ReflectionTestUtils.setField(
                request,
                "receiverPhone",
                RECEIVER_PHONE
        );

        ReflectionTestUtils.setField(
                request,
                "deliveryAddress",
                DELIVERY_ADDRESS
        );

        ReflectionTestUtils.setField(
                request,
                "deliveryAddressDetail",
                DELIVERY_ADDRESS_DETAIL
        );

        ReflectionTestUtils.setField(
                request,
                "deliveryMemo",
                DELIVERY_MEMO
        );

        return request;
    }

    // 상품 1개에 대한 주문 요청 생성
    private OrderCreateRequest createSingleOrderRequest(Integer quantity) {

        return createOrderRequest(
                List.of(
                        createItemRequest(PRD_ID, quantity)
                ),
                null
        );
    }

    // 상품 조회 및 팝업 운영 상태 설정
    private Product mockProduct(Long prdId, String opStatus) {

        Product product = mock(Product.class);
        PopupStore popupStore = mock(PopupStore.class);

        when(productRepository.findById(prdId)).thenReturn(Optional.of(product));
        when(product.getPopupStore()).thenReturn(popupStore);
        when(popupStore.getOpStatus()).thenReturn(opStatus);

        return product;
    }

    // 1인당 구매 제한 검증 설정
    private void mockPurchaseLimit(Product product, Long prdId, int purchasedQuantity, int maxQty) {

        when(product.getPrdId()).thenReturn(prdId);
        when(product.getMaxQty()).thenReturn(maxQty);
        when(orderItemRepository.sumPurchasedQuantity(
                MEMBER_ID,
                prdId,
                ACTIVE_STATUSES
        )).thenReturn(purchasedQuantity);
    }

    // 주문 가능한 상품 설정
    private Product mockAvailableProduct(Long prdId, int unitPrice, int quantity) {

        Product product = mockProduct(
                prdId,
                "OPEN"
        );

        mockPurchaseLimit(
                product,
                prdId,
                0,
                MAX_QTY
        );

        when(product.getPrice()).thenReturn(unitPrice);

        when(productRepository.decreaseStock(
                prdId,
                quantity
        )).thenReturn(1);

        return product;
    }

    // 주문 저장 시 실제 DB의 ID 할당 동작 모방
    private void mockOrderSave() {

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> {

                    Order order = invocation.getArgument(0);

                    ReflectionTestUtils.setField(
                            order,
                            "orderId",
                            ORDER_ID
                    );

                    return order;
                });
    }

    // 실제 주문 엔티티 생성
    private Order createOrder(User user, int totalPrice, OrderStatus orderStatus) {

        Order order = Order.createOrder(
                user,
                totalPrice,
                LocalDateTime.now().plusMinutes(10),
                RECEIVER_NAME,
                RECEIVER_PHONE,
                DELIVERY_ADDRESS,
                DELIVERY_ADDRESS_DETAIL,
                DELIVERY_MEMO,
                null
        );

        ReflectionTestUtils.setField(
                order,
                "orderId",
                ORDER_ID
        );

        switch (orderStatus) {

            case PAID -> order.completePayment();

            case CANCELLED -> order.cancel();

            case EXPIRED -> order.expire();

            default -> {
                // PENDING 상태 유지
            }
        }

        return order;
    }

    // 주문 상품 추가
    private void addOrderItem(Order order, Long prdId, int quantity, int unitPrice) {

        Product product = mock(Product.class);

        when(product.getPrdId()).thenReturn(prdId);

        OrderItem orderItem = OrderItem.createOrderItem(
                        product,
                        quantity,
                        unitPrice,
                        unitPrice * quantity
        );

        order.addOrderItem(orderItem);
    }

    // 주문 생성 실패 시 후속 처리 미실행 검증
    private void verifyOrderCreationNotExecuted() {

        verifyNoInteractions(
                orderRepository,
                couponService,
                cartService,
                eventPublisher,
                redisExpirationKeyService,
                paymentService
        );
    }

    @Test
    @DisplayName("존재하지 않는 회원은 주문을 생성할 수 없다")
    void createOrder_userNotFound() {

        OrderCreateRequest request = createSingleOrderRequest(2);

        when(userRepository.findByUsernameForUpdate(USERNAME)).thenReturn(Optional.empty());

        AuthException exception = assertThrows(
                AuthException.class,
                () -> orderService.createOrder(USERNAME, request)
        );

        assertEquals(
                AuthErrorCode.USER_NOT_FOUND,
                exception.getErrorCode()
        );

        verifyNoInteractions(productRepository, orderItemRepository);

        verifyOrderCreationNotExecuted();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, 0})
    @DisplayName("주문 수량이 null 또는 0 이하이면 주문할 수 없다")
    void createOrder_invalidQuantity(Integer quantity) {

        mockLoginUser();

        OrderCreateRequest request = createSingleOrderRequest(quantity);

        OrderException exception = assertThrows(
                OrderException.class,
                () -> orderService.createOrder(USERNAME, request)
        );

        assertEquals(
                OrderErrorCode.INVALID_QUANTITY,
                exception.getErrorCode()
        );

        verifyNoInteractions(
                productRepository,
                orderItemRepository
        );

        verifyOrderCreationNotExecuted();
    }

    @Test
    @DisplayName("존재하지 않는 상품은 주문할 수 없다")
    void createOrder_productNotFound() {

        mockLoginUser();

        OrderCreateRequest request = createSingleOrderRequest(2);

        when(productRepository.findById(PRD_ID)).thenReturn(Optional.empty());

        ProductException exception = assertThrows(
                ProductException.class,
                () -> orderService.createOrder(USERNAME, request)
        );

        assertEquals(
                ProductErrorCode.PRODUCT_NOT_FOUND,
                exception.getErrorCode()
        );

        verify(productRepository, never()).decreaseStock(anyLong(), anyInt());

        verifyNoInteractions(orderItemRepository);

        verifyOrderCreationNotExecuted();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAUSED", "CANCELLED"})
    @DisplayName("운영 중인 팝업이 아니면 상품을 주문할 수 없다")
    void createOrder_popupNotOpen(String opStatus) {

        mockLoginUser();

        OrderCreateRequest request = createSingleOrderRequest(2);

        mockProduct(PRD_ID, opStatus);

        OrderException exception = assertThrows(
                OrderException.class,
                () -> orderService.createOrder(USERNAME, request)
        );

        assertEquals(
                OrderErrorCode.POPUP_NOT_OPEN,
                exception.getErrorCode()
        );

        verify(productRepository, never()).decreaseStock(anyLong(), anyInt());

        verifyNoInteractions(orderItemRepository);

        verifyOrderCreationNotExecuted();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("판매 시작 전이거나 판매 종료 이후에는 주문할 수 없다")
    void createOrder_invalidSalePeriod(boolean beforeSaleStart) {

        mockLoginUser();

        OrderCreateRequest request = createSingleOrderRequest(2);

        Product product = mockProduct(PRD_ID, "OPEN");

        OrderErrorCode expectedErrorCode;

        if (beforeSaleStart) {

            when(product.getSaleStart()).thenReturn(LocalDateTime.now().plusDays(1));

            expectedErrorCode = OrderErrorCode.SALE_NOT_STARTED;

        } else {

            when(product.getSaleStart()).thenReturn(LocalDateTime.now().minusDays(2));
            when(product.getSaleEnd()).thenReturn(LocalDateTime.now().minusDays(1));

            expectedErrorCode = OrderErrorCode.SALE_ENDED;
        }

        OrderException exception = assertThrows(
                OrderException.class,
                () -> orderService.createOrder(USERNAME, request)
        );

        assertEquals(
                expectedErrorCode,
                exception.getErrorCode()
        );

        verify(productRepository, never()).decreaseStock(anyLong(), anyInt());

        verifyNoInteractions(orderItemRepository);

        verifyOrderCreationNotExecuted();
    }

    @Test
    @DisplayName("기존 구매 수량과 요청 수량의 합이 구매 제한을 초과하면 주문할 수 없다")
    void createOrder_purchaseLimitExceeded() {

        mockLoginUser();

        OrderCreateRequest request = createSingleOrderRequest(3);

        Product product = mockProduct(PRD_ID, "OPEN");

        // 기존 구매 3개 + 요청 3개 > 최대 5개
        mockPurchaseLimit(
                product,
                PRD_ID,
                3,
                5
        );

        OrderException exception = assertThrows(
                OrderException.class,
                () -> orderService.createOrder(USERNAME, request)
        );

        assertEquals(
                OrderErrorCode.PURCHASE_LIMIT_EXCEEDED,
                exception.getErrorCode()
        );

        verify(orderItemRepository).sumPurchasedQuantity(MEMBER_ID, PRD_ID, ACTIVE_STATUSES);

        verify(productRepository, never()).decreaseStock(anyLong(), anyInt());

        verifyOrderCreationNotExecuted();
    }

    @Test
    @DisplayName("상품 재고가 부족하면 주문을 생성할 수 없다")
    void createOrder_insufficientStock() {

        mockLoginUser();

        OrderCreateRequest request = createSingleOrderRequest(3);

        Product product = mockProduct(PRD_ID, "OPEN"
        );

        mockPurchaseLimit(
                product,
                PRD_ID,
                0,
                5
        );

        // 조건부 UPDATE 실패
        when(productRepository.decreaseStock(PRD_ID, 3)).thenReturn(0);

        OrderException exception = assertThrows(
                OrderException.class,
                () -> orderService.createOrder(USERNAME, request)
        );

        assertEquals(
                OrderErrorCode.INSUFFICIENT_STOCK,
                exception.getErrorCode()
        );

        verify(productRepository).decreaseStock(PRD_ID, 3);

        verifyOrderCreationNotExecuted();
    }

    @Test
    @DisplayName("정상 주문 생성 시 재고 차감, 금액 계산, 배송 정보 저장 및 이벤트 발행이 수행된다")
    void createOrder_success() {

        Long firstPrdId = 2L;
        Long secondPrdId = 5L;

        User user = mockLoginUser();

        Product firstProduct = mockAvailableProduct(
                firstPrdId,
                10000,
                2
        );

        Product secondProduct = mockAvailableProduct(
                secondPrdId,
                15000,
                3
        );

        // 상품 ID를 역순으로 요청하여 정렬 검증
        OrderCreateRequest request =
                createOrderRequest(
                        List.of(
                                createItemRequest(secondPrdId, 3),
                                createItemRequest(firstPrdId, 2)
                        ),
                        null
                );

        mockOrderSave();

        LocalDateTime before = LocalDateTime.now();

        OrderResponse response = orderService.createOrder(USERNAME, request);

        LocalDateTime after = LocalDateTime.now();

        int expectedTotalPrice = (10000 * 2) + (15000 * 3);

        // 응답 검증
        assertEquals(ORDER_ID, response.orderId());
        assertEquals(expectedTotalPrice, response.totalPrice());
        assertEquals(OrderStatus.PENDING, response.orderStatus());

        assertNotNull(response.tossOrderId());
        assertNotNull(response.orderedAt());
        assertNull(response.paymentAt());

        // 주문 만료 시간 10분 검증
        assertFalse(response.expiresAt().isBefore(before.plusMinutes(10)));

        assertFalse(response.expiresAt().isAfter(after.plusMinutes(10)));

        // 실제 저장된 주문 엔티티 검증
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);

        // 주문 만료 이벤트 검증
        ArgumentCaptor<OrderPendingCreatedEvent> eventCaptor = ArgumentCaptor.forClass(OrderPendingCreatedEvent.class);

        // 상품 ID 정렬 및 후속 처리 순서 검증
        InOrder inOrder = inOrder(
                userRepository,
                productRepository,
                orderRepository,
                cartService,
                eventPublisher
        );

        inOrder.verify(userRepository).findByUsernameForUpdate(USERNAME);
        inOrder.verify(productRepository).decreaseStock(firstPrdId, 2);
        inOrder.verify(productRepository).decreaseStock(secondPrdId, 3);
        inOrder.verify(orderRepository).save(orderCaptor.capture());

        // 장바구니 삭제는 원래 요청 순서를 사용
        inOrder.verify(cartService).removeItemsByProductIds(USERNAME, List.of(secondPrdId, firstPrdId));
        inOrder.verify(eventPublisher).publishEvent(eventCaptor.capture());

        Order savedOrder = orderCaptor.getValue();

        assertSame(user, savedOrder.getUser());

        assertEquals(expectedTotalPrice, savedOrder.getTotalPrice());
        assertEquals(OrderStatus.PENDING, savedOrder.getOrderStatus());
        assertEquals(response.tossOrderId(), savedOrder.getTossOrderId());

        // 배송 정보 검증
        assertEquals(RECEIVER_NAME, savedOrder.getReceiverName());
        assertEquals(RECEIVER_PHONE, savedOrder.getReceiverPhone());
        assertEquals(DELIVERY_ADDRESS, savedOrder.getDeliveryAddress());
        assertEquals(DELIVERY_ADDRESS_DETAIL, savedOrder.getDeliveryAddressDetail());
        assertEquals(DELIVERY_MEMO, savedOrder.getDeliveryMemo());

        assertNull(savedOrder.getUserCoupon());

        // 주문 상품 검증
        List<OrderItem> items = savedOrder.getOrderItems();

        assertEquals(2, items.size());

        OrderItem firstItem = items.get(0);
        OrderItem secondItem = items.get(1);

        // 첫 번째 상품
        assertSame(firstProduct, firstItem.getProduct());

        assertEquals(2, firstItem.getQuantity());
        assertEquals(10000, firstItem.getUnitPrice());
        assertEquals(20000, firstItem.getPrice());

        assertSame(savedOrder, firstItem.getOrder());

        // 두 번째 상품
        assertSame(secondProduct, secondItem.getProduct());

        assertEquals(3, secondItem.getQuantity());
        assertEquals(15000, secondItem.getUnitPrice());
        assertEquals(45000, secondItem.getPrice());

        assertSame(savedOrder, secondItem.getOrder());

        // 주문 상품 합계와 주문 총 금액 일치
        int itemTotalPrice = items.stream()
                .mapToInt(OrderItem::getPrice)
                .sum();

        assertEquals(savedOrder.getTotalPrice(), itemTotalPrice);

        // 이벤트 데이터 검증
        OrderPendingCreatedEvent event = eventCaptor.getValue();

        assertEquals(ORDER_ID, event.orderId());
        assertEquals(10, event.expireMinutes());

        verifyNoInteractions(
                couponService,
                paymentService,
                redisExpirationKeyService
        );
    }

    @Test
    @DisplayName("쿠폰 적용 시 할인된 주문 금액과 사용 쿠폰 정보가 저장된다")
    void createOrder_withCoupon() {

        mockLoginUser();

        mockAvailableProduct(
                PRD_ID,
                UNIT_PRICE,
                3
        );

        UserCoupon userCoupon = mock(UserCoupon.class);

        OrderCreateRequest request =
                createOrderRequest(
                        List.of(
                                createItemRequest(PRD_ID, 3)
                        ),
                        COUPON_ID
                );

        int originalPrice = 30000;
        int discountedPrice = 25000;

        when(couponService.getValidUserCoupon(USERNAME, COUPON_ID)).thenReturn(userCoupon);
        when(couponService.calculateDiscountedPrice(USERNAME, COUPON_ID, originalPrice)).thenReturn(discountedPrice);

        mockOrderSave();

        OrderResponse response = orderService.createOrder(USERNAME, request);

        assertEquals(discountedPrice, response.totalPrice());
        assertEquals(OrderStatus.PENDING, response.orderStatus());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);

        InOrder inOrder = inOrder(
                productRepository,
                couponService,
                orderRepository,
                cartService,
                eventPublisher
        );

        inOrder.verify(productRepository).decreaseStock(PRD_ID, 3);

        inOrder.verify(couponService).getValidUserCoupon(USERNAME, COUPON_ID);
        inOrder.verify(couponService).calculateDiscountedPrice(USERNAME, COUPON_ID, originalPrice);
        inOrder.verify(orderRepository).save(orderCaptor.capture());
        inOrder.verify(cartService).removeItemsByProductIds(USERNAME, List.of(PRD_ID));
        inOrder.verify(eventPublisher).publishEvent(new OrderPendingCreatedEvent(ORDER_ID, 10));

        Order savedOrder = orderCaptor.getValue();

        // 쿠폰 정보 저장 검증
        assertSame(userCoupon, savedOrder.getUserCoupon());

        // 할인된 주문 총 금액 검증
        assertEquals(discountedPrice, savedOrder.getTotalPrice());

        // 주문 상품 금액은 할인 전 금액 유지
        assertEquals(originalPrice,
                savedOrder.getOrderItems()
                        .get(0)
                        .getPrice()
        );

        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("로그인 회원의 주문 목록을 정상적으로 조회한다")
    void getMyOrders_success() {

        User user = createUser();

        Order firstOrder = createOrder(
                user,
                20000,
                OrderStatus.PENDING
        );

        Order secondOrder = createOrder(
                user,
                30000,
                OrderStatus.PAID
        );

        ReflectionTestUtils.setField(
                secondOrder,
                "orderId",
                ORDER_ID + 1
        );

        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(orderRepository.findByMemberId(MEMBER_ID)).thenReturn(List.of(firstOrder, secondOrder));

        List<OrderResponse> response = orderService.getMyOrders(USERNAME);

        assertEquals(2, response.size());
        assertEquals(firstOrder.getOrderId(), response.get(0).orderId());
        assertEquals(20000, response.get(0).totalPrice());
        assertEquals(OrderStatus.PENDING, response.get(0).orderStatus());
        assertEquals(secondOrder.getOrderId(), response.get(1).orderId());
        assertEquals(30000, response.get(1).totalPrice());
        assertEquals(OrderStatus.PAID, response.get(1).orderStatus());

        verify(orderRepository).findByMemberId(MEMBER_ID);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("다른 회원의 주문을 조회하거나 취소할 수 없다")
    void order_accessDenied(boolean detailRequest) {

        User user = createUser();

        Order order = createOrder(
                user,
                20000,
                OrderStatus.PENDING
        );

        OrderException exception;

        if (detailRequest) {

            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            exception = assertThrows(
                    OrderException.class,
                    () -> orderService.getOrderDetail(OTHER_USERNAME, ORDER_ID)
            );

        } else {

            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

            exception = assertThrows(
                    OrderException.class,
                    () -> orderService.cancelOrder(OTHER_USERNAME, ORDER_ID)
            );
        }

        assertEquals(OrderErrorCode.ORDER_ACCESS_DENIED, exception.getErrorCode());
        assertEquals(OrderStatus.PENDING, order.getOrderStatus());

        verifyNoInteractions(
                productRepository,
                paymentService,
                redisExpirationKeyService
        );
    }

    @Test
    @DisplayName("본인의 주문 상세 정보를 정상적으로 조회한다")
    void getOrderDetail_success() {

        User user = createUser();

        Order order = createOrder(
                user,
                20000,
                OrderStatus.PAID
        );

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        OrderDetailResponse response = orderService.getOrderDetail(USERNAME, ORDER_ID);

        assertEquals(ORDER_ID, response.orderId());
        assertEquals(order.getTotalPrice(), response.totalPrice());
        assertEquals(OrderStatus.PAID, response.orderStatus());
        assertEquals(order.getOrderedAt(), response.orderedAt());
        assertEquals(order.getPaymentAt(), response.paymentAt());
        assertEquals(order.getExpiresAt(), response.expiresAt());

        assertNotNull(response.items());
        assertTrue(response.items().isEmpty());

        verify(orderRepository).findById(ORDER_ID);
    }

    @ParameterizedTest
    @EnumSource(
            value = OrderStatus.class,
            names = {"CANCELLED", "EXPIRED"}
    )
    @DisplayName("이미 취소되거나 만료된 주문은 취소할 수 없다")
    void cancelOrder_invalidStatus(OrderStatus orderStatus) {

        User user = createUser();

        Order order = createOrder(
                user,
                20000,
                orderStatus
        );

        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        OrderErrorCode expectedErrorCode =
                orderStatus == OrderStatus.CANCELLED ? OrderErrorCode.ORDER_ALREADY_CANCELLED : OrderErrorCode.ORDER_EXPIRED;

        OrderException exception = assertThrows(
                OrderException.class,
                () -> orderService.cancelOrder(USERNAME, ORDER_ID)
        );

        assertEquals(expectedErrorCode, exception.getErrorCode());

        // 기존 주문 상태 유지
        assertEquals(orderStatus, order.getOrderStatus());

        // 중복 재고 복구 및 결제 취소 방지
        verifyNoInteractions(
                productRepository,
                paymentService,
                redisExpirationKeyService
        );
    }

    @Test
    @DisplayName("결제 대기 주문 취소 시 재고를 복구하고 Redis 만료 키를 삭제한다")
    void cancelOrder_pendingSuccess() {

        User user = createUser();

        Order order = createOrder(
                user,
                50000,
                OrderStatus.PENDING
        );

        Long secondPrdId = 5L;

        addOrderItem(
                order,
                PRD_ID,
                2,
                10000
        );

        addOrderItem(
                order,
                secondPrdId,
                3,
                10000
        );

        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(productRepository.increaseStock(PRD_ID, 2)).thenReturn(1);when(productRepository.increaseStock(secondPrdId, 3)).thenReturn(1);

        OrderResponse response = orderService.cancelOrder(USERNAME, ORDER_ID);

        assertEquals(OrderStatus.CANCELLED, order.getOrderStatus());
        assertEquals(OrderStatus.CANCELLED, response.orderStatus());
        assertEquals(ORDER_ID, response.orderId());

        // 모든 상품 재고 복구 및 TTL 삭제 순서 검증
        InOrder inOrder = inOrder(
                orderRepository,
                productRepository,
                redisExpirationKeyService
        );

        inOrder.verify(orderRepository).findByIdForUpdate(ORDER_ID);
        inOrder.verify(productRepository).increaseStock(PRD_ID, 2);
        inOrder.verify(productRepository).increaseStock(secondPrdId, 3);
        inOrder.verify(redisExpirationKeyService).delete("order:pending:" + ORDER_ID);

        // 결제 전 주문이므로 결제 취소 미실행
        verifyNoInteractions(paymentService);

        // 영속 엔티티 상태 변경 방식이므로
        // 별도의 save 호출 없음
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("결제 완료 주문 취소 시 결제 취소 후 재고를 복구한다")
    void cancelOrder_paidSuccess() {

        User user = createUser();

        Order order = createOrder(
                user,
                20000,
                OrderStatus.PAID
        );

        addOrderItem(
                order,
                PRD_ID,
                2,
                UNIT_PRICE
        );

        LocalDateTime originalPaymentAt = order.getPaymentAt();

        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(productRepository.increaseStock(PRD_ID, 2)).thenReturn(1);

        OrderResponse response = orderService.cancelOrder(USERNAME, ORDER_ID);

        assertEquals(OrderStatus.CANCELLED, order.getOrderStatus());
        assertEquals(OrderStatus.CANCELLED, response.orderStatus());

        // 기존 결제 시간 유지
        assertEquals(originalPaymentAt, order.getPaymentAt());

        // 결제 취소 이후 재고 복구 순서 검증
        InOrder inOrder = inOrder(
                orderRepository,
                paymentService,
                productRepository
        );

        inOrder.verify(orderRepository).findByIdForUpdate(ORDER_ID);
        inOrder.verify(paymentService).cancelPayment(order);
        inOrder.verify(productRepository).increaseStock(PRD_ID, 2);

        // PAID 주문은 결제 승인 시 TTL이 삭제되는 구조
        // 현재 주문 취소 로직에서는 별도로 삭제하지 않음
        verifyNoInteractions(redisExpirationKeyService);

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("재고 복구 실패 시 주문 취소가 완료되지 않는다")
    void cancelOrder_stockRestoreFailed() {

        User user = createUser();

        Order order = createOrder(
                user,
                20000,
                OrderStatus.PENDING
        );

        addOrderItem(
                order,
                PRD_ID,
                2,
                UNIT_PRICE
        );

        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        // 재고 복구 UPDATE 실패
        when(productRepository.increaseStock(PRD_ID, 2)).thenReturn(0);

        OrderException exception = assertThrows(
                OrderException.class,
                () -> orderService.cancelOrder(USERNAME, ORDER_ID)
        );

        assertEquals(OrderErrorCode.STOCK_RESTORE_FAILED, exception.getErrorCode());

        // 취소 완료되지 않고 기존 상태 유지
        assertEquals(OrderStatus.PENDING, order.getOrderStatus());

        verify(productRepository).increaseStock(PRD_ID, 2);

        // 재고 복구 실패 시 Redis 키 유지
        verifyNoInteractions(
                redisExpirationKeyService,
                paymentService
        );

        verify(orderRepository, never()).save(any(Order.class));
    }
}
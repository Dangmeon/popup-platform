package com.popq.platform.payment.integration;

import com.popq.platform.auth.entity.Role;
import com.popq.platform.auth.entity.User;
import com.popq.platform.auth.repository.UserRepository;
import com.popq.platform.order.dto.OrderCreateRequest;
import com.popq.platform.order.dto.OrderItemRequest;
import com.popq.platform.order.dto.OrderResponse;
import com.popq.platform.order.entity.Order;
import com.popq.platform.order.entity.OrderStatus;
import com.popq.platform.order.redis.OrderExpirationService;
import com.popq.platform.order.repository.OrderRepository;
import com.popq.platform.order.service.OrderService;
import com.popq.platform.order.service.OrderExpirationScheduler;
import com.popq.platform.payment.dto.PaymentRequest;
import com.popq.platform.payment.dto.PaymentResponse;
import com.popq.platform.payment.dto.TossPaymentResponse;
import com.popq.platform.payment.entity.*;
import com.popq.platform.payment.exception.PaymentErrorCode;
import com.popq.platform.payment.exception.PaymentException;
import com.popq.platform.payment.repository.PaymentOperationRepository;
import com.popq.platform.payment.repository.PaymentRepository;
import com.popq.platform.payment.service.PaymentService;
import com.popq.platform.payment.service.PaymentReconciliationScheduler;
import com.popq.platform.popup.entity.PopupStore;
import com.popq.platform.popup.repository.PopupStoreRepository;
import com.popq.platform.product.entity.Product;
import com.popq.platform.product.repository.ProductRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=20"
})
class PaymentRecoveryIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderExpirationService orderExpirationService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentOperationRepository paymentOperationRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PopupStoreRepository popupStoreRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    // 실제 Toss API 호출 방지
    @MockitoBean
    private RestClient restClient;

    // 테스트 중 자동 스케줄러 실행 방지
    @MockitoBean
    private PaymentReconciliationScheduler paymentReconciliationScheduler;

    @MockitoBean
    private OrderExpirationScheduler orderExpirationScheduler;


    // 공통 테스트 데이터

    private static final int UNIT_PRICE = 10000;
    private static final int INITIAL_STOCK = 10;
    private static final int QUANTITY = 2;

    private static final String CONFIRM_URL =
            "https://api.tosspayments.com/v1/payments/confirm";

    private static final String CANCEL_URL =
            "https://api.tosspayments.com/v1/payments/{paymentKey}/cancel";

    private static final String LOOKUP_URL =
            "https://api.tosspayments.com/v1/payments/{paymentKey}";

    private Long userId;
    private Long popId;
    private Long prdId;

    private User user;
    private PopupStore popupStore;

    private AtomicInteger confirmApiCount;
    private AtomicInteger cancelApiCount;
    private AtomicInteger lookupApiCount;


    // 초기 설정 및 데이터 정리

    @BeforeEach
    void setUp() {

        userId = null;
        popId = null;
        prdId = null;

        confirmApiCount = new AtomicInteger(0);
        cancelApiCount = new AtomicInteger(0);
        lookupApiCount = new AtomicInteger(0);

        user = createUser();
        popupStore = createPopupStore();

        createProduct();
    }

    @AfterEach
    void tearDown() {

        if (userId != null) {

            List<Order> orders = orderRepository.findByMemberId(userId);

            for (Order order : orders) {

                Long orderId = order.getOrderId();

                // Redis 만료 키 삭제
                stringRedisTemplate.delete("order:pending:" + orderId);

                // 결제 처리 이력 삭제
                paymentOperationRepository.deleteAll(
                        paymentOperationRepository.findAllByOrderId(orderId)
                );

                // 결제 정보 삭제
                paymentRepository.findByOrder_OrderId(orderId)
                        .ifPresent(paymentRepository::delete);
            }

            // 주문 및 주문 상품 삭제
            orderRepository.deleteAll(orders);
        }

        if (prdId != null) {
            productRepository.deleteById(prdId);
        }

        if (popId != null) {
            popupStoreRepository.deleteById(popId);
        }

        if (userId != null) {
            userRepository.deleteById(userId);
        }
    }


    // 테스트 데이터 생성

    private User createUser() {

        String uniqueId = UUID.randomUUID().toString();

        String phone = "010" + ThreadLocalRandom.current()
                .nextInt(10000000, 100000000);

        User testUser = User.builder()
                .username("test-" + uniqueId + "@test.com")
                .password("testPassword")
                .name("결제 복구 테스트 회원")
                .phone(phone)
                .role(Role.USER)
                .build();

        User savedUser = userRepository.saveAndFlush(testUser);

        userId = savedUser.getId();

        return savedUser;
    }

    private PopupStore createPopupStore() {

        LocalDate today = LocalDate.now();

        PopupStore popup = PopupStore.builder()
                .title("결제 복구 테스트 팝업")
                .location("테스트 장소")
                .startDate(today)
                .endDate(today.plusDays(7))
                .category("ETC")
                .description("결제 복구 테스트용 팝업")
                .storeImg("test.jpg")
                .roundNum(1)
                .reservStart("14:00")
                .reservEnd("15:00")
                .reservPeopleCap(5)
                .opStatus("OPEN")
                .build();

        PopupStore savedPopup = popupStoreRepository.saveAndFlush(popup);

        popId = savedPopup.getPopId();

        return savedPopup;
    }

    private void createProduct() {

        Product product = Product.builder()
                .popupStore(popupStore)
                .productName("결제 복구 테스트 상품")
                .price(UNIT_PRICE)
                .stock(INITIAL_STOCK)
                .maxQty(10)
                .saleStart(LocalDateTime.now().minusDays(1))
                .saleEnd(LocalDateTime.now().plusDays(5))
                .category("ETC")
                .productImg("test.jpg")
                .productDesc("결제 복구 테스트 상품")
                .build();

        Product savedProduct = productRepository.saveAndFlush(product);

        prdId = savedProduct.getPrdId();
    }

    private OrderCreateRequest createOrderRequest() {

        OrderItemRequest item = new OrderItemRequest();

        ReflectionTestUtils.setField(item, "prdId", prdId);
        ReflectionTestUtils.setField(item, "quantity", QUANTITY);

        OrderCreateRequest request = new OrderCreateRequest();

        ReflectionTestUtils.setField(request, "items", List.of(item));
        ReflectionTestUtils.setField(request, "receiverName", "테스트 회원");
        ReflectionTestUtils.setField(request, "receiverPhone", "01012345678");
        ReflectionTestUtils.setField(request, "deliveryAddress", "테스트 주소");
        ReflectionTestUtils.setField(request, "deliveryAddressDetail", "101호");
        ReflectionTestUtils.setField(request, "deliveryMemo", "문 앞에 놓아주세요");

        return request;
    }

    private PaymentRequest createPaymentRequest(
            String tossOrderId, String paymentKey, int amount
    ) {

        PaymentRequest request = new PaymentRequest();

        ReflectionTestUtils.setField(request, "orderId", tossOrderId);
        ReflectionTestUtils.setField(request, "paymentKey", paymentKey);
        ReflectionTestUtils.setField(request, "amount", amount);

        return request;
    }

    private String createPaymentKey() {
        return "test-payment-" + UUID.randomUUID();
    }


    // ==================== 공통 조회 메서드 ====================

    private Order getOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow();
    }

    private Payment getPayment(Long orderId) {
        return paymentRepository.findByOrder_OrderId(orderId).orElseThrow();
    }

    private PaymentOperation getOperation(Long operationId) {
        return paymentOperationRepository.findById(operationId).orElseThrow();
    }

    private PaymentOperation getPaymentOperation(
            Long orderId, PaymentOperationType type
    ) {

        return paymentOperationRepository.findAllByOrderId(orderId)
                .stream()
                .filter(operation -> operation.getOperationType() == type)
                .findFirst()
                .orElseThrow();
    }

    private int getCurrentStock() {

        return productRepository.findById(prdId)
                .orElseThrow()
                .getStock();
    }

    private boolean hasRedisKey(Long orderId) {

        return Boolean.TRUE.equals(
                stringRedisTemplate.hasKey("order:pending:" + orderId)
        );
    }


    // Toss API Mock

    // 결제 승인 성공
    private void mockTossConfirmSuccess() {

        RestClient.RequestBodyUriSpec uriSpec =
                mock(RestClient.RequestBodyUriSpec.class);

        RestClient.RequestBodySpec bodySpec =
                mock(RestClient.RequestBodySpec.class);

        RestClient.ResponseSpec responseSpec =
                mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(CONFIRM_URL)).thenReturn(bodySpec);
        when(bodySpec.headers(any())).thenReturn(bodySpec);
        when(bodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(bodySpec);
        when(bodySpec.body(any(PaymentRequest.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);

        when(responseSpec.toBodilessEntity()).thenAnswer(invocation -> {

            confirmApiCount.incrementAndGet();

            return ResponseEntity.ok().build();
        });
    }

    // 결제 취소 성공 또는 통신 실패
    private void mockTossCancel(String paymentKey, boolean success) {

        RestClient.RequestBodyUriSpec uriSpec =
                mock(RestClient.RequestBodyUriSpec.class);

        RestClient.RequestBodySpec bodySpec =
                mock(RestClient.RequestBodySpec.class);

        RestClient.ResponseSpec responseSpec =
                mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(CANCEL_URL, paymentKey)).thenReturn(bodySpec);
        when(bodySpec.headers(any())).thenReturn(bodySpec);
        when(bodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(bodySpec);
        when(bodySpec.body(anyMap())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);

        when(responseSpec.toBodilessEntity()).thenAnswer(invocation -> {

            cancelApiCount.incrementAndGet();

            if (!success) {
                throw new ResourceAccessException("Toss 결제 취소 통신 실패");
            }

            return ResponseEntity.ok().build();
        });
    }

    // Toss 결제 상태 조회
    private void mockTossLookup(
            String paymentKey, String tossOrderId,
            String status, int amount, int balanceAmount
    ) {

        RestClient.RequestHeadersUriSpec<?> uriSpec =
                mock(RestClient.RequestHeadersUriSpec.class);

        RestClient.RequestHeadersSpec<?> headersSpec =
                mock(RestClient.RequestHeadersSpec.class);

        RestClient.ResponseSpec responseSpec =
                mock(RestClient.ResponseSpec.class);

        doReturn(uriSpec).when(restClient).get();

        doReturn(headersSpec)
                .when(uriSpec).uri(LOOKUP_URL, paymentKey);

        doReturn(headersSpec)
                .when(headersSpec).headers(any());

        when(headersSpec.retrieve()).thenReturn(responseSpec);

        TossPaymentResponse response = new TossPaymentResponse(
                paymentKey,
                tossOrderId,
                status,
                amount,
                balanceAmount,
                "2026-11-26T15:00:00+09:00"
        );

        when(responseSpec.body(TossPaymentResponse.class))
                .thenAnswer(invocation -> {

                    lookupApiCount.incrementAndGet();

                    return response;
                });
    }


    // 공통 결제 처리

    // 정상 결제 승인
    private PaymentResponse confirmPayment(
            OrderResponse order, String paymentKey
    ) {

        mockTossConfirmSuccess();

        PaymentRequest request = createPaymentRequest(
                order.tossOrderId(),
                paymentKey,
                order.totalPrice()
        );

        PaymentResponse response = paymentService.confirmPayment(
                user.getUsername(), request
        );

        assertEquals(PaymentStatus.PAID, response.paymentStatus());

        return response;
    }

    // 테스트용 강제 롤백 예외
    private static class ForcedRollbackException extends RuntimeException {
    }


    // 결제 승인 강제 롤백

    private PaymentOperation confirmAndRollback(
            OrderResponse order, String paymentKey
    ) {

        Long orderId = order.orderId();

        mockTossConfirmSuccess();

        PaymentRequest request = createPaymentRequest(
                order.tossOrderId(),
                paymentKey,
                order.totalPrice()
        );

        // 실제 결제 승인 이후 강제 롤백
        assertThrows(
                ForcedRollbackException.class,
                () -> transactionTemplate.executeWithoutResult(status -> {

                    PaymentResponse response = paymentService.confirmPayment(
                            user.getUsername(), request
                    );

                    assertEquals(PaymentStatus.PAID, response.paymentStatus());

                    paymentRepository.flush();

                    // 롤백 전 상태 검증
                    assertEquals(
                            OrderStatus.PAID,
                            getOrder(orderId).getOrderStatus()
                    );

                    assertEquals(
                            PaymentStatus.PAID,
                            getPayment(orderId).getPaymentStatus()
                    );

                    assertEquals(
                            PaymentOperationStatus.COMPLETED,
                            getPaymentOperation(
                                    orderId, PaymentOperationType.CONFIRM
                            ).getOperationStatus()
                    );

                    // 커밋 전에는 Redis 키 유지
                    assertTrue(hasRedisKey(orderId));

                    throw new ForcedRollbackException();
                })
        );

        // 실제 DB 롤백 이후 검증
        assertEquals(
                OrderStatus.PENDING,
                getOrder(orderId).getOrderStatus()
        );

        assertNull(getOrder(orderId).getPaymentAt());

        assertTrue(
                paymentRepository.findByOrder_OrderId(orderId).isEmpty()
        );

        PaymentOperation operation = getPaymentOperation(
                orderId, PaymentOperationType.CONFIRM
        );

        // 독립 저장한 결제 처리 이력 유지
        assertEquals(PaymentOperationStatus.PENDING, operation.getOperationStatus());
        assertEquals(orderId, operation.getOrderId());
        assertEquals(paymentKey, operation.getPaymentKey());
        assertEquals(order.totalPrice(), operation.getAmount());

        assertEquals(
                1, paymentOperationRepository.findAllByOrderId(orderId).size()
        );

        assertEquals(INITIAL_STOCK - QUANTITY, getCurrentStock());
        assertEquals(1, confirmApiCount.get());

        assertTrue(hasRedisKey(orderId));

        System.out.println("===== 결제 승인 이후 DB 롤백 =====");
        System.out.println("주문 상태: " + getOrder(orderId).getOrderStatus());
        System.out.println("처리 이력 상태: " + operation.getOperationStatus());
        System.out.println("현재 재고: " + getCurrentStock());
        System.out.println("Toss 승인 API 호출: " + confirmApiCount.get());
        System.out.println("Redis 키 존재 여부: " + hasRedisKey(orderId));

        return operation;
    }


    // 결제 취소 강제 롤백

    private PaymentOperation cancelAndRollback(
            OrderResponse order, String paymentKey
    ) {

        Long orderId = order.orderId();

        Long originalPaymentId = getPayment(orderId).getPaymentId();

        mockTossCancel(paymentKey, true);

        // 실제 결제 취소 이후 강제 롤백
        assertThrows(
                ForcedRollbackException.class,
                () -> transactionTemplate.executeWithoutResult(status -> {

                    OrderResponse response = orderService.cancelOrder(
                            user.getUsername(), orderId
                    );

                    assertEquals(OrderStatus.CANCELLED, response.orderStatus());

                    paymentRepository.flush();

                    // 롤백 전 상태 검증
                    assertEquals(
                            PaymentStatus.CANCELLED,
                            getPayment(orderId).getPaymentStatus()
                    );

                    assertEquals(
                            PaymentOperationStatus.COMPLETED,
                            getPaymentOperation(
                                    orderId, PaymentOperationType.CANCEL
                            ).getOperationStatus()
                    );

                    assertEquals(INITIAL_STOCK, getCurrentStock());

                    throw new ForcedRollbackException();
                })
        );

        // 실제 DB 롤백 이후 상태 검증
        assertEquals(
                OrderStatus.PAID,
                getOrder(orderId).getOrderStatus()
        );

        Payment payment = getPayment(orderId);

        assertEquals(PaymentStatus.PAID, payment.getPaymentStatus());
        assertEquals(originalPaymentId, payment.getPaymentId());
        assertNull(payment.getCanceledAt());

        PaymentOperation confirmOperation = getPaymentOperation(
                orderId, PaymentOperationType.CONFIRM
        );

        PaymentOperation cancelOperation = getPaymentOperation(
                orderId, PaymentOperationType.CANCEL
        );

        // 기존 승인 작업은 완료 상태 유지
        assertEquals(
                PaymentOperationStatus.COMPLETED,
                confirmOperation.getOperationStatus()
        );

        // 취소 처리 이력은 PENDING 유지
        assertEquals(
                PaymentOperationStatus.PENDING,
                cancelOperation.getOperationStatus()
        );

        assertEquals(paymentKey, cancelOperation.getPaymentKey());

        assertEquals(
                2, paymentOperationRepository.findAllByOrderId(orderId).size()
        );

        // 재고 복구도 롤백되어야 함
        assertEquals(INITIAL_STOCK - QUANTITY, getCurrentStock());

        assertEquals(1, confirmApiCount.get());
        assertEquals(1, cancelApiCount.get());

        assertFalse(hasRedisKey(orderId));

        System.out.println("===== 결제 취소 이후 DB 롤백 =====");
        System.out.println("주문 상태: " + getOrder(orderId).getOrderStatus());
        System.out.println("결제 상태: " + payment.getPaymentStatus());
        System.out.println("취소 처리 이력: " + cancelOperation.getOperationStatus());
        System.out.println("현재 재고: " + getCurrentStock());

        return cancelOperation;
    }

    @Test
    @DisplayName("Toss 결제 승인 이후 DB 롤백이 발생해도 결제를 복구할 수 있다")
    void recoverConfirmPayment_afterRollback() {

        OrderResponse order = orderService.createOrder(
                user.getUsername(), createOrderRequest()
        );

        Long orderId = order.orderId();
        String paymentKey = createPaymentKey();

        PaymentOperation operation = confirmAndRollback(order, paymentKey);

        Long operationId = operation.getOperationId();

        // Toss에서는 결제가 성공한 상태
        mockTossLookup(
                paymentKey, order.tossOrderId(),
                "DONE", order.totalPrice(), order.totalPrice()
        );

        // 결제 복구
        paymentService.recoverConfirmPayment(orderId, operationId);

        Payment recoveredPayment = getPayment(orderId);

        assertEquals(OrderStatus.PAID, getOrder(orderId).getOrderStatus());
        assertNotNull(getOrder(orderId).getPaymentAt());

        assertEquals(PaymentStatus.PAID, recoveredPayment.getPaymentStatus());
        assertEquals(paymentKey, recoveredPayment.getPaymentKey());
        assertEquals(order.totalPrice(), recoveredPayment.getAmount());

        assertEquals(
                PaymentOperationStatus.COMPLETED,
                getOperation(operationId).getOperationStatus()
        );

        assertEquals(
                1, paymentOperationRepository.findAllByOrderId(orderId).size()
        );

        assertEquals(INITIAL_STOCK - QUANTITY, getCurrentStock());

        // 외부 승인 API는 다시 호출하지 않음
        assertEquals(1, confirmApiCount.get());
        assertEquals(1, lookupApiCount.get());

        assertFalse(hasRedisKey(orderId));

        System.out.println("===== 결제 승인 복구 성공 =====");
        System.out.println("최종 주문 상태: " + getOrder(orderId).getOrderStatus());
        System.out.println("최종 결제 상태: " + recoveredPayment.getPaymentStatus());
        System.out.println("처리 이력: " + getOperation(operationId).getOperationStatus());
        System.out.println("Toss 승인 API 호출: " + confirmApiCount.get());
        System.out.println("Toss 조회 API 호출: " + lookupApiCount.get());
        System.out.println("최종 재고: " + getCurrentStock());
    }

    @Test
    @DisplayName("동일한 결제를 여러 번 복구해도 중복 처리되지 않는다")
    void recoverConfirmPayment_duplicateRecovery() {

        OrderResponse order = orderService.createOrder(
                user.getUsername(), createOrderRequest()
        );

        Long orderId = order.orderId();
        String paymentKey = createPaymentKey();

        PaymentOperation operation = confirmAndRollback(order, paymentKey);

        Long operationId = operation.getOperationId();

        mockTossLookup(
                paymentKey, order.tossOrderId(),
                "DONE", order.totalPrice(), order.totalPrice()
        );

        // 최초 복구
        paymentService.recoverConfirmPayment(orderId, operationId);

        Long originalPaymentId = getPayment(orderId).getPaymentId();

        LocalDateTime originalPaidAt = getPayment(orderId).getPaidAt();
        LocalDateTime originalUpdatedAt = getOperation(operationId).getUpdatedAt();

        // 중복 복구
        paymentService.recoverConfirmPayment(orderId, operationId);
        paymentService.recoverConfirmPayment(orderId, operationId);

        assertEquals(OrderStatus.PAID, getOrder(orderId).getOrderStatus());
        assertEquals(PaymentStatus.PAID, getPayment(orderId).getPaymentStatus());

        assertEquals(originalPaymentId, getPayment(orderId).getPaymentId());
        assertEquals(originalPaidAt, getPayment(orderId).getPaidAt());

        assertEquals(
                PaymentOperationStatus.COMPLETED,
                getOperation(operationId).getOperationStatus()
        );

        // 완료된 작업을 다시 갱신하지 않음
        assertEquals(
                originalUpdatedAt,
                getOperation(operationId).getUpdatedAt()
        );

        assertEquals(
                1, paymentOperationRepository.findAllByOrderId(orderId).size()
        );

        assertEquals(INITIAL_STOCK - QUANTITY, getCurrentStock());

        assertEquals(1, confirmApiCount.get());
        assertEquals(1, lookupApiCount.get());

        assertFalse(hasRedisKey(orderId));

        System.out.println("===== 중복 결제 승인 복구 방지 성공 =====");
        System.out.println("전체 복구 요청: 3회");
        System.out.println("Toss 승인 API 호출: " + confirmApiCount.get());
        System.out.println("Toss 조회 API 호출: " + lookupApiCount.get());
        System.out.println("최종 재고: " + getCurrentStock());
    }

    @Test
    @DisplayName("미완료 결제 처리 이력이 있으면 주문을 만료시키지 않는다")
    void expireOrder_unresolvedPayment() {

        OrderResponse order = orderService.createOrder(
                user.getUsername(), createOrderRequest()
        );

        Long orderId = order.orderId();
        String paymentKey = createPaymentKey();

        PaymentOperation operation = confirmAndRollback(order, paymentKey);

        Long operationId = operation.getOperationId();

        // 주문 생성 및 만료 시간을 과거로 변경
        transactionTemplate.executeWithoutResult(status -> {

            Order targetOrder = getOrder(orderId);

            LocalDateTime now = LocalDateTime.now();

            ReflectionTestUtils.setField(
                    targetOrder, "orderedAt", now.minusMinutes(15)
            );

            ReflectionTestUtils.setField(
                    targetOrder, "expiresAt", now.minusMinutes(5)
            );

            orderRepository.flush();
        });

        assertTrue(
                getOrder(orderId).getExpiresAt().isBefore(LocalDateTime.now())
        );

        // 실제 주문 만료 서비스 실행
        orderExpirationService.expireOrder(orderId);

        // 미완료 결제 이력이 존재하므로 주문 및 재고 보호
        assertEquals(OrderStatus.PENDING, getOrder(orderId).getOrderStatus());

        assertEquals(
                PaymentOperationStatus.PENDING,
                getOperation(operationId).getOperationStatus()
        );

        assertTrue(
                paymentRepository.findByOrder_OrderId(orderId).isEmpty()
        );

        assertEquals(INITIAL_STOCK - QUANTITY, getCurrentStock());

        // Toss 결제 성공 확인
        mockTossLookup(
                paymentKey, order.tossOrderId(),
                "DONE", order.totalPrice(), order.totalPrice()
        );

        // 만료 시간이 지났더라도 기존 승인 결제 복구 가능
        paymentService.recoverConfirmPayment(orderId, operationId);

        assertEquals(OrderStatus.PAID, getOrder(orderId).getOrderStatus());
        assertEquals(PaymentStatus.PAID, getPayment(orderId).getPaymentStatus());

        assertEquals(
                PaymentOperationStatus.COMPLETED,
                getOperation(operationId).getOperationStatus()
        );

        assertEquals(INITIAL_STOCK - QUANTITY, getCurrentStock());

        assertEquals(1, confirmApiCount.get());
        assertEquals(1, lookupApiCount.get());

        assertFalse(hasRedisKey(orderId));

        System.out.println("===== 미완료 결제 주문 만료 방지 성공 =====");
        System.out.println("최종 주문 상태: " + getOrder(orderId).getOrderStatus());
        System.out.println("최종 재고: " + getCurrentStock());
    }

    @Test
    @DisplayName("Toss 결제 취소 이후 DB 롤백이 발생해도 취소를 복구할 수 있다")
    void recoverCancelPayment_afterRollback() {

        OrderResponse order = orderService.createOrder(
                user.getUsername(), createOrderRequest()
        );

        Long orderId = order.orderId();
        String paymentKey = createPaymentKey();

        // 정상 결제 승인
        PaymentResponse response = confirmPayment(order, paymentKey);

        Long originalPaymentId = response.paymentId();

        // 취소 이후 강제 롤백
        PaymentOperation operation = cancelAndRollback(order, paymentKey);

        Long operationId = operation.getOperationId();

        // Toss에서는 이미 전액 취소된 상태
        mockTossLookup(
                paymentKey, order.tossOrderId(),
                "CANCELED", order.totalPrice(), 0
        );

        // PaymentService에 통합한 취소 복구 실행
        paymentService.recoverCancelPayment(orderId, operationId);

        Payment recoveredPayment = getPayment(orderId);

        assertEquals(OrderStatus.CANCELLED, getOrder(orderId).getOrderStatus());

        assertEquals(PaymentStatus.CANCELLED, recoveredPayment.getPaymentStatus());
        assertEquals(originalPaymentId, recoveredPayment.getPaymentId());

        assertNotNull(recoveredPayment.getCanceledAt());

        assertEquals(
                PaymentOperationStatus.COMPLETED,
                getOperation(operationId).getOperationStatus()
        );

        assertEquals(
                PaymentOperationStatus.COMPLETED,
                getPaymentOperation(
                        orderId, PaymentOperationType.CONFIRM
                ).getOperationStatus()
        );

        assertEquals(
                2, paymentOperationRepository.findAllByOrderId(orderId).size()
        );

        // 재고 정상 복구
        assertEquals(INITIAL_STOCK, getCurrentStock());

        assertEquals(1, confirmApiCount.get());
        assertEquals(1, cancelApiCount.get());
        assertEquals(1, lookupApiCount.get());

        assertFalse(hasRedisKey(orderId));

        System.out.println("===== 결제 취소 복구 성공 =====");
        System.out.println("최종 주문 상태: " + getOrder(orderId).getOrderStatus());
        System.out.println("최종 결제 상태: " + recoveredPayment.getPaymentStatus());
        System.out.println("취소 처리 이력: " + getOperation(operationId).getOperationStatus());
        System.out.println("Toss 취소 API 호출: " + cancelApiCount.get());
        System.out.println("Toss 조회 API 호출: " + lookupApiCount.get());
        System.out.println("최종 재고: " + getCurrentStock());
    }

    @Test
    @DisplayName("결제 취소를 여러 번 복구해도 상품 재고가 중복 복구되지 않는다")
    void recoverCancelPayment_duplicateRecovery() {

        OrderResponse order = orderService.createOrder(
                user.getUsername(), createOrderRequest()
        );

        Long orderId = order.orderId();
        String paymentKey = createPaymentKey();

        confirmPayment(order, paymentKey);

        PaymentOperation operation = cancelAndRollback(order, paymentKey);

        Long operationId = operation.getOperationId();

        mockTossLookup(
                paymentKey, order.tossOrderId(),
                "CANCELED", order.totalPrice(), 0
        );

        // 최초 복구
        paymentService.recoverCancelPayment(orderId, operationId);

        Long originalPaymentId = getPayment(orderId).getPaymentId();

        LocalDateTime originalCanceledAt = getPayment(orderId).getCanceledAt();
        LocalDateTime originalUpdatedAt = getOperation(operationId).getUpdatedAt();

        assertEquals(INITIAL_STOCK, getCurrentStock());

        // 중복 복구 요청
        paymentService.recoverCancelPayment(orderId, operationId);
        paymentService.recoverCancelPayment(orderId, operationId);

        assertEquals(OrderStatus.CANCELLED, getOrder(orderId).getOrderStatus());

        assertEquals(
                PaymentStatus.CANCELLED,
                getPayment(orderId).getPaymentStatus()
        );

        assertEquals(originalPaymentId, getPayment(orderId).getPaymentId());
        assertEquals(originalCanceledAt, getPayment(orderId).getCanceledAt());

        assertEquals(
                PaymentOperationStatus.COMPLETED,
                getOperation(operationId).getOperationStatus()
        );

        assertEquals(
                originalUpdatedAt,
                getOperation(operationId).getUpdatedAt()
        );

        assertEquals(
                2, paymentOperationRepository.findAllByOrderId(orderId).size()
        );

        // 중복 복구 이후에도 재고는 10개
        assertEquals(INITIAL_STOCK, getCurrentStock());

        assertEquals(1, confirmApiCount.get());
        assertEquals(1, cancelApiCount.get());
        assertEquals(1, lookupApiCount.get());

        System.out.println("===== 중복 결제 취소 복구 방지 성공 =====");
        System.out.println("전체 복구 요청: 3회");
        System.out.println("Toss 취소 API 호출: " + cancelApiCount.get());
        System.out.println("Toss 조회 API 호출: " + lookupApiCount.get());
        System.out.println("최종 재고: " + getCurrentStock());
    }

    @Test
    @DisplayName("외부 결제 취소가 확인되지 않으면 주문 및 재고 상태를 유지한다")
    void recoverCancelPayment_unresolved() {

        OrderResponse order = orderService.createOrder(
                user.getUsername(), createOrderRequest()
        );

        Long orderId = order.orderId();
        String paymentKey = createPaymentKey();

        confirmPayment(order, paymentKey);

        // Toss 취소 API 통신 오류
        mockTossCancel(paymentKey, false);

        PaymentException exception = assertThrows(
                PaymentException.class,
                () -> orderService.cancelOrder(user.getUsername(), orderId)
        );

        assertEquals(
                PaymentErrorCode.PAYMENT_CONNECTION_ERROR,
                exception.getErrorCode()
        );

        PaymentOperation operation = getPaymentOperation(
                orderId, PaymentOperationType.CANCEL
        );

        Long operationId = operation.getOperationId();

        assertEquals(
                PaymentOperationStatus.PENDING,
                operation.getOperationStatus()
        );

        // Toss에서는 아직 결제가 완료된 상태
        mockTossLookup(
                paymentKey, order.tossOrderId(),
                "DONE", order.totalPrice(), order.totalPrice()
        );

        // 취소 복구 시도
        paymentService.recoverCancelPayment(orderId, operationId);

        // 외부 취소가 확인되지 않았으므로 기존 상태 유지
        assertEquals(OrderStatus.PAID, getOrder(orderId).getOrderStatus());
        assertEquals(PaymentStatus.PAID, getPayment(orderId).getPaymentStatus());

        assertNull(getPayment(orderId).getCanceledAt());

        // RECOVERY_REQUIRED 대신 PENDING 유지
        assertEquals(
                PaymentOperationStatus.PENDING,
                getOperation(operationId).getOperationStatus()
        );

        // 재고 복구 방지
        assertEquals(INITIAL_STOCK - QUANTITY, getCurrentStock());

        assertEquals(
                2, paymentOperationRepository.findAllByOrderId(orderId).size()
        );

        assertEquals(1, confirmApiCount.get());
        assertEquals(1, cancelApiCount.get());
        assertEquals(1, lookupApiCount.get());

        System.out.println("===== 미확정 결제 취소 상태 보호 성공 =====");
        System.out.println("최종 주문 상태: " + getOrder(orderId).getOrderStatus());
        System.out.println("최종 결제 상태: " + getPayment(orderId).getPaymentStatus());
        System.out.println("취소 처리 이력: " + getOperation(operationId).getOperationStatus());
        System.out.println("최종 재고: " + getCurrentStock());
    }
}
package com.popq.platform.payment.concurrency;

import com.popq.platform.auth.entity.Role;
import com.popq.platform.auth.entity.User;
import com.popq.platform.auth.repository.UserRepository;
import com.popq.platform.order.dto.OrderCreateRequest;
import com.popq.platform.order.dto.OrderItemRequest;
import com.popq.platform.order.dto.OrderResponse;
import com.popq.platform.order.entity.Order;
import com.popq.platform.order.entity.OrderItem;
import com.popq.platform.order.entity.OrderStatus;
import com.popq.platform.order.exception.OrderErrorCode;
import com.popq.platform.order.exception.OrderException;
import com.popq.platform.order.redis.OrderExpirationService;
import com.popq.platform.order.repository.OrderItemRepository;
import com.popq.platform.order.repository.OrderRepository;
import com.popq.platform.order.service.OrderExpirationScheduler;
import com.popq.platform.order.service.OrderService;
import com.popq.platform.payment.dto.PaymentRequest;
import com.popq.platform.payment.dto.PaymentResponse;
import com.popq.platform.payment.entity.Payment;
import com.popq.platform.payment.entity.PaymentStatus;
import com.popq.platform.payment.entity.PaymentOperation;
import com.popq.platform.payment.entity.PaymentOperationStatus;
import com.popq.platform.payment.entity.PaymentOperationType;
import com.popq.platform.payment.repository.PaymentRepository;
import com.popq.platform.payment.repository.PaymentOperationRepository;
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
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=20",
        "logging.level.com.zaxxer.hikari=DEBUG"
})
class PaymentConcurrencyTest {

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
    private OrderItemRepository orderItemRepository;

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

    // 자동 복구 스케줄러의 테스트 개입 방지
    @MockitoBean
    private PaymentReconciliationScheduler paymentReconciliationScheduler;

    // 자동 주문 만료 스케줄러의 테스트 개입 방지
    @MockitoBean
    private OrderExpirationScheduler orderExpirationScheduler;

    // 공통 테스트 데이터
    private static final int UNIT_PRICE = 10000;
    private static final String CONFIRM_URL = "https://api.tosspayments.com/v1/payments/confirm";
    private static final String CANCEL_URL = "https://api.tosspayments.com/v1/payments/{paymentKey}/cancel";
    private static final List<OrderStatus> ACTIVE_STATUSES = List.of(OrderStatus.PENDING, OrderStatus.PAID);

    private final List<Long> userIds = new ArrayList<>();

    private Long popId;
    private Long prdId;
    private PopupStore popupStore;

    private AtomicInteger confirmApiCount;
    private AtomicInteger cancelApiCount;

    // 테스트 초기 설정 및 데이터 정리

    @BeforeEach
    void setUp() {
        userIds.clear();

        popId = null;
        prdId = null;

        confirmApiCount = new AtomicInteger(0);
        cancelApiCount = new AtomicInteger(0);

        popupStore = createPopupStore();
    }

    @AfterEach
    void tearDown() {
        List<Order> orders = findTestOrders();

        // Redis 주문 만료 키 삭제
        for (Order order : orders) {
            stringRedisTemplate.delete("order:pending:" + order.getOrderId());
        }

        // 독립 트랜잭션으로 저장된 결제 처리 이력 삭제
        for (Order order : orders) {
            List<PaymentOperation> operations = paymentOperationRepository.findAllByOrderId(order.getOrderId());

            paymentOperationRepository.deleteAll(operations);
        }

        // 외래 키 제약조건을 고려하여 결제 정보 먼저 삭제
        for (Order order : orders) {
            paymentRepository.findByOrder_OrderId(order.getOrderId()).ifPresent(paymentRepository::delete);
        }

        // 주문 및 주문 상품 삭제
        orderRepository.deleteAll(orders);

        // 테스트 상품 삭제
        if (prdId != null) {
            productRepository.deleteById(prdId);
        }

        // 테스트 팝업 삭제
        if (popId != null) {
            popupStoreRepository.deleteById(popId);
        }

        // 테스트 회원 삭제
        for (Long userId : userIds) {
            userRepository.deleteById(userId);
        }

        userIds.clear();

        popId = null;
        prdId = null;
        popupStore = null;
    }

    // 공통 테스트 데이터 생성 메서드

    // 테스트 회원 생성
    private User createUser() {
        String uniqueId = UUID.randomUUID().toString();
        String phone = "010" + ThreadLocalRandom.current().nextInt(10000000, 100000000);

        User user = User.builder()
                .username("test-" + uniqueId + "@test.com")
                .password("testPassword")
                .name("결제 테스트 회원")
                .phone(phone)
                .role(Role.USER)
                .build();

        User savedUser = userRepository.saveAndFlush(user);
        userIds.add(savedUser.getId());

        return savedUser;
    }

    // 테스트 팝업 생성
    private PopupStore createPopupStore() {
        LocalDate today = LocalDate.now();

        PopupStore popup = PopupStore.builder()
                .title("결제 동시성 테스트 팝업")
                .location("테스트 장소")
                .startDate(today)
                .endDate(today.plusDays(7))
                .category("ETC")
                .description("결제 동시성 테스트용 팝업")
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

    // 테스트 상품 생성
    private Product createProduct(int stock, int maxQty) {
        Product product = Product.builder()
                .popupStore(popupStore)
                .productName("결제 동시성 테스트 상품")
                .price(UNIT_PRICE)
                .stock(stock)
                .maxQty(maxQty)
                .saleStart(LocalDateTime.now().minusDays(1))
                .saleEnd(LocalDateTime.now().plusDays(5))
                .category("ETC")
                .productImg("test.jpg")
                .productDesc("결제 동시성 테스트 상품")
                .build();

        Product savedProduct = productRepository.saveAndFlush(product);
        prdId = savedProduct.getPrdId();

        return savedProduct;
    }

    // 실제 주문 요청 DTO 생성
    private OrderCreateRequest createOrderRequest(int quantity) {
        OrderItemRequest item = new OrderItemRequest();

        ReflectionTestUtils.setField(item, "prdId", prdId);
        ReflectionTestUtils.setField(item, "quantity", quantity);

        OrderCreateRequest request = new OrderCreateRequest();

        ReflectionTestUtils.setField(request, "items", List.of(item));
        ReflectionTestUtils.setField(request, "receiverName", "테스트 회원");
        ReflectionTestUtils.setField(request, "receiverPhone", "01012345678");
        ReflectionTestUtils.setField(request, "deliveryAddress", "테스트 주소");
        ReflectionTestUtils.setField(request, "deliveryAddressDetail", "101호");
        ReflectionTestUtils.setField(request, "deliveryMemo", "문 앞에 놓아주세요");

        return request;
    }

    // 실제 결제 요청 DTO 생성
    private PaymentRequest createPaymentRequest(String tossOrderId, String paymentKey, int amount) {
        PaymentRequest request = new PaymentRequest();

        ReflectionTestUtils.setField(request, "orderId", tossOrderId);
        ReflectionTestUtils.setField(request, "paymentKey", paymentKey);
        ReflectionTestUtils.setField(request, "amount", amount);

        return request;
    }

    // 테스트용 고유 결제 키 생성
    private String createPaymentKey() {
        return "test-payment-" + UUID.randomUUID();
    }

    // 만료 시간이 지난 PENDING 주문 생성
    private Order createExpiredPendingOrder(User user, int quantity) {
        return transactionTemplate.execute(status -> {
            Product product = productRepository.findById(prdId).orElseThrow();
            User managedUser = userRepository.findById(user.getId()).orElseThrow();

            // 실제 DB 재고 차감
            int updatedRows = productRepository.decreaseStock(prdId, quantity);
            assertEquals(1, updatedRows);

            // 만료 시간이 지난 주문 생성
            Order order = Order.createOrder(
                    managedUser, UNIT_PRICE * quantity,
                    LocalDateTime.now().minusMinutes(5),
                    "테스트 회원", "01012345678", "테스트 주소",
                    "101호", "문 앞에 놓아주세요", null
            );

            // 주문 생성 시간도 과거로 설정
            ReflectionTestUtils.setField(order, "orderedAt", LocalDateTime.now().minusMinutes(15));

            OrderItem orderItem = OrderItem.createOrderItem(product, quantity, UNIT_PRICE, UNIT_PRICE * quantity);

            order.addOrderItem(orderItem);

            // 실제 DB 저장 및 트랜잭션 커밋
            return orderRepository.saveAndFlush(order);
        });
    }

    // 테스트 회원들의 실제 주문 목록 조회
    private List<Order> findTestOrders() {
        List<Order> orders = new ArrayList<>();

        for (Long userId : userIds) {
            orders.addAll(orderRepository.findByMemberId(userId));
        }

        return orders;
    }

    // 실제 DB 상품 재고 조회
    private int getCurrentStock() {
        return productRepository.findById(prdId).orElseThrow().getStock();
    }

    // 실제 DB 활성 주문 수량 합계 조회
    private int getActivePurchasedQuantity() {
        return userIds.stream()
                .mapToInt(userId -> orderItemRepository.sumPurchasedQuantity(userId, prdId, ACTIVE_STATUSES))
                .sum();
    }

    // 테스트 주문에 연결된 실제 결제 개수 조회
    private long getTestPaymentCount() {
        return findTestOrders().stream()
                .filter(order -> paymentRepository.findByOrder_OrderId(order.getOrderId()).isPresent())
                .count();
    }

    // 테스트 주문에 연결된 실제 결제 처리 이력 개수 조회
    private long getTestOperationCount() {
        return findTestOrders().stream()
                .mapToLong(order -> paymentOperationRepository.findAllByOrderId(order.getOrderId()).size())
                .sum();
    }

    // Toss API Mock 설정

    // 결제 승인 및 취소 API 성공 응답 설정
    private void mockTossApiSuccess() {
        RestClient.RequestBodyUriSpec requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec confirmSpec = mock(RestClient.RequestBodySpec.class);
        RestClient.RequestBodySpec cancelSpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec confirmResponseSpec = mock(RestClient.ResponseSpec.class);
        RestClient.ResponseSpec cancelResponseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(requestBodyUriSpec);

        // 결제 승인 API
        when(requestBodyUriSpec.uri(CONFIRM_URL)).thenReturn(confirmSpec);
        when(confirmSpec.headers(any())).thenReturn(confirmSpec);
        when(confirmSpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(confirmSpec);
        when(confirmSpec.body(any(PaymentRequest.class))).thenReturn(confirmSpec);
        when(confirmSpec.retrieve()).thenReturn(confirmResponseSpec);

        when(confirmResponseSpec.toBodilessEntity()).thenAnswer(invocation -> {
            confirmApiCount.incrementAndGet();
            return ResponseEntity.ok().build();
        });

        // 결제 취소 API
        when(requestBodyUriSpec.uri(eq(CANCEL_URL), anyString())).thenReturn(cancelSpec);
        when(cancelSpec.headers(any())).thenReturn(cancelSpec);
        when(cancelSpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(cancelSpec);
        when(cancelSpec.body(anyMap())).thenReturn(cancelSpec);
        when(cancelSpec.retrieve()).thenReturn(cancelResponseSpec);

        when(cancelResponseSpec.toBodilessEntity()).thenAnswer(invocation -> {
            cancelApiCount.incrementAndGet();
            return ResponseEntity.ok().build();
        });
    }

    // 공통 동시성 실행 메서드

    private void executeConcurrently(List<Runnable> tasks) throws Exception {
        int threadCount = tasks.size();

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<?>> futures = new ArrayList<>();

        try {
            for (Runnable task : tasks) {
                Future<?> future = executorService.submit(() -> {
                    // 스레드 준비 완료
                    readyLatch.countDown();

                    // 동시 실행 신호 대기
                    startLatch.await();

                    // 실제 서비스 호출
                    task.run();

                    return null;
                });

                futures.add(future);
            }

            // 모든 스레드 준비 완료 대기
            assertTrue(readyLatch.await(10, TimeUnit.SECONDS), "스레드 준비 시간 초과");

            // 동시에 요청 시작
            startLatch.countDown();

            // 모든 스레드 실행 결과 및 예외 확인
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }

        } finally {
            executorService.shutdownNow();

            assertTrue(executorService.awaitTermination(10, TimeUnit.SECONDS), "스레드 종료 시간 초과");
        }
    }

    @Test
    @DisplayName("동일 주문을 동시에 결제 승인해도 실제 결제는 한 번만 처리되고 동일한 결제 정보가 반환된다")
    void confirmPayment_concurrentDuplicate() throws Exception {

        int threadCount = 10;
        int initialStock = 10;
        int quantity = 2;

        createProduct(initialStock, 10);

        User user = createUser();

        // 실제 주문 생성 및 재고 차감
        OrderResponse order = orderService.createOrder(user.getUsername(), createOrderRequest(quantity));

        Long orderId = order.orderId();
        String tossOrderId = order.tossOrderId();
        String paymentKey = createPaymentKey();

        assertEquals(OrderStatus.PENDING, order.orderStatus());
        assertEquals(initialStock - quantity, getCurrentStock());

        assertTrue(paymentRepository.findByOrder_OrderId(orderId).isEmpty());
        assertTrue(paymentOperationRepository.findAllByOrderId(orderId).isEmpty());

        // Toss 결제 승인 성공 응답 설정
        mockTossApiSuccess();

        AtomicInteger successCount = new AtomicInteger(0);

        // 모든 요청의 결제 응답 저장
        ConcurrentLinkedQueue<PaymentResponse> responses = new ConcurrentLinkedQueue<>();

        List<Runnable> tasks = new ArrayList<>();

        // 동일 주문 및 결제 키에 대한 승인 요청 10건 생성
        for (int i = 0; i < threadCount; i++) {

            PaymentRequest request = createPaymentRequest(tossOrderId, paymentKey, order.totalPrice());

            tasks.add(() -> {
                PaymentResponse response = paymentService.confirmPayment(user.getUsername(), request);

                assertNotNull(response.paymentId());
                assertEquals(orderId, response.orderId());
                assertEquals(paymentKey, response.paymentKey());
                assertEquals(order.totalPrice(), response.amount());
                assertEquals(PaymentStatus.PAID, response.paymentStatus());

                responses.add(response);

                successCount.incrementAndGet();
            });
        }

        executeConcurrently(tasks);

        // 멱등성 처리에 의해 모든 결제 요청 정상 응답
        assertEquals(threadCount, successCount.get());
        assertEquals(threadCount, responses.size());

        // Toss 결제 승인 API는 한 번만 호출
        assertEquals(1, confirmApiCount.get());
        assertEquals(0, cancelApiCount.get());

        verify(restClient, times(1)).post();

        // 실제 DB 주문 상태 검증
        Order savedOrder = orderRepository.findById(orderId).orElseThrow();

        assertEquals(OrderStatus.PAID, savedOrder.getOrderStatus());
        assertNotNull(savedOrder.getPaymentAt());

        // 실제 DB 결제 상태 검증
        Payment savedPayment = paymentRepository.findByOrder_OrderId(orderId).orElseThrow();

        assertNotNull(savedPayment.getPaymentId());
        assertEquals(PaymentStatus.PAID, savedPayment.getPaymentStatus());
        assertEquals(paymentKey, savedPayment.getPaymentKey());
        assertEquals(order.totalPrice(), savedPayment.getAmount());
        assertNotNull(savedPayment.getPaidAt());
        assertNull(savedPayment.getCanceledAt());

        // 모든 요청에 동일한 결제 정보가 반환되는지 검증
        for (PaymentResponse response : responses) {
            assertEquals(savedPayment.getPaymentId(), response.paymentId());
            assertEquals(savedPayment.getPaymentKey(), response.paymentKey());
            assertEquals(savedPayment.getAmount(), response.amount());
            assertEquals(PaymentStatus.PAID, response.paymentStatus());
        }

        // 중복 결제 데이터 생성 방지
        assertEquals(1, findTestOrders().size());
        assertEquals(1, getTestPaymentCount());

        // 실제 결제 처리 이력 검증
        List<PaymentOperation> operations = paymentOperationRepository.findAllByOrderId(orderId);

        assertEquals(1, operations.size());
        assertEquals(1, getTestOperationCount());

        PaymentOperation operation = operations.get(0);

        assertNotNull(operation.getOperationId());
        assertEquals(orderId, operation.getOrderId());
        assertEquals(tossOrderId, operation.getTossOrderId());
        assertEquals(paymentKey, operation.getPaymentKey());
        assertEquals(order.totalPrice(), operation.getAmount());
        assertEquals("confirm-" + tossOrderId, operation.getIdempotencyKey());
        assertEquals(PaymentOperationType.CONFIRM, operation.getOperationType());
        assertEquals(PaymentOperationStatus.COMPLETED, operation.getOperationStatus());

        // 결제 과정에서 재고가 추가 차감되지 않아야 함
        int finalStock = getCurrentStock();
        int activeQuantity = getActivePurchasedQuantity();

        assertEquals(8, finalStock);
        assertEquals(quantity, activeQuantity);
        assertEquals(initialStock, finalStock + activeQuantity);

        // DB 커밋 이후 Redis 만료 키 삭제 검증
        assertFalse(Boolean.TRUE.equals(stringRedisTemplate.hasKey("order:pending:" + orderId)));

        // 테스트 결과 출력
        System.out.println("===== 결제 승인 멱등성 동시성 테스트 결과 =====");
        System.out.println("전체 결제 요청: " + threadCount);
        System.out.println("정상 응답: " + successCount.get());
        System.out.println("Toss 승인 API 호출 횟수: " + confirmApiCount.get());
        System.out.println("실제 DB 결제 개수: " + getTestPaymentCount());
        System.out.println("실제 결제 처리 이력 개수: " + getTestOperationCount());
        System.out.println("결제 처리 이력 상태: " + operation.getOperationStatus());
        System.out.println("동일 결제 ID 반환 여부: " + responses.stream().allMatch(response -> savedPayment.getPaymentId().equals(response.paymentId())));
        System.out.println("최종 주문 상태: " + savedOrder.getOrderStatus());
        System.out.println("최종 결제 상태: " + savedPayment.getPaymentStatus());
        System.out.println("결제 금액: " + savedPayment.getAmount());
        System.out.println("초기 재고: " + initialStock);
        System.out.println("최종 재고: " + finalStock);
        System.out.println("활성 주문 수량 합계: " + activeQuantity);
        System.out.println("Redis 만료 키 존재 여부: " + stringRedisTemplate.hasKey("order:pending:" + orderId));
    }

    @Test
    @DisplayName("만료 시간이 지난 주문의 결제 승인과 만료 처리가 동시에 실행되어도 정합성이 유지된다")
    void confirmAndExpire_concurrent() throws Exception {

        int initialStock = 10;
        int expiredQuantity = 2;
        int normalQuantity = 3;

        createProduct(initialStock, 10);

        User expiredUser = createUser();
        User normalUser = createUser();

        // 다른 회원의 정상 주문 생성
        OrderResponse normalOrder = orderService.createOrder(normalUser.getUsername(), createOrderRequest(normalQuantity));

        // 만료 시간이 지난 PENDING 주문 생성
        Order expiredOrder = createExpiredPendingOrder(expiredUser, expiredQuantity);

        Long expiredOrderId = expiredOrder.getOrderId();

        assertEquals(OrderStatus.PENDING, expiredOrder.getOrderStatus());
        assertEquals(OrderStatus.PENDING, normalOrder.orderStatus());

        // 초기 재고 10 - 정상 주문 3 - 만료 대상 2
        int beforeStock = getCurrentStock();

        assertEquals(5, beforeStock);

        PaymentRequest request = createPaymentRequest(
                expiredOrder.getTossOrderId(),
                createPaymentKey(),
                expiredOrder.getTotalPrice()
        );

        mockTossApiSuccess();

        AtomicInteger confirmSuccessCount = new AtomicInteger(0);
        AtomicInteger confirmRejectedCount = new AtomicInteger(0);
        AtomicInteger expireCompletedCount = new AtomicInteger(0);

        List<Runnable> tasks = new ArrayList<>();

        // Thread A: 결제 승인
        tasks.add(() -> {
            try {
                paymentService.confirmPayment(expiredUser.getUsername(), request);

                confirmSuccessCount.incrementAndGet();

            } catch (OrderException e) {
                // 결제 승인이 먼저 락을 획득하면 ORDER_EXPIRED
                // 주문 만료가 먼저 실행되면 INVALID_ORDER_STATUS
                assertTrue(
                        e.getErrorCode() == OrderErrorCode.ORDER_EXPIRED ||
                                e.getErrorCode() == OrderErrorCode.INVALID_ORDER_STATUS
                );

                confirmRejectedCount.incrementAndGet();

                System.out.println("결제 승인 거절 사유: " + e.getErrorCode());
            }
        });

        // Thread B: 주문 만료
        tasks.add(() -> {
            orderExpirationService.expireOrder(expiredOrderId);

            expireCompletedCount.incrementAndGet();
        });

        executeConcurrently(tasks);

        // Then
        assertEquals(0, confirmSuccessCount.get());
        assertEquals(1, confirmRejectedCount.get());
        assertEquals(1, expireCompletedCount.get());

        // 만료 주문은 Toss API 자체를 호출하지 않아야 함
        assertEquals(0, confirmApiCount.get());
        assertEquals(0, cancelApiCount.get());

        verifyNoInteractions(restClient);

        // 실제 DB 만료 대상 주문 검증
        Order expiredResult = orderRepository.findById(expiredOrderId).orElseThrow();

        assertEquals(OrderStatus.EXPIRED, expiredResult.getOrderStatus());
        assertNull(expiredResult.getPaymentAt());

        // 결제 정보가 생성되지 않아야 함
        assertTrue(paymentRepository.findByOrder_OrderId(expiredOrderId).isEmpty());
        assertEquals(0, getTestPaymentCount());

        // 만료 주문은 결제 처리 이력도 생성되지 않아야 함
        assertTrue(paymentOperationRepository.findAllByOrderId(expiredOrderId).isEmpty());
        assertEquals(0, getTestOperationCount());

        // 다른 정상 주문은 영향을 받지 않아야 함
        Order normalResult = orderRepository.findById(normalOrder.orderId()).orElseThrow();

        assertEquals(OrderStatus.PENDING, normalResult.getOrderStatus());

        // 만료 대상 주문 수량만 복구되어야 함
        int finalStock = getCurrentStock();
        int restoredStock = finalStock - beforeStock;

        assertEquals(expiredQuantity, restoredStock);
        assertEquals(7, finalStock);

        // 활성 주문 수량 검증
        int activeQuantity = getActivePurchasedQuantity();

        assertEquals(normalQuantity, activeQuantity);
        assertEquals(initialStock, finalStock + activeQuantity);

        assertEquals(2, findTestOrders().size());

        // 정상 주문의 Redis 만료 키 유지
        assertTrue(Boolean.TRUE.equals(stringRedisTemplate.hasKey("order:pending:" + normalOrder.orderId())));

        // 테스트 결과 출력
        System.out.println("===== 결제 승인 및 주문 만료 동시성 테스트 결과 =====");
        System.out.println("전체 요청: 2");
        System.out.println("결제 승인 성공: " + confirmSuccessCount.get());
        System.out.println("결제 승인 거절: " + confirmRejectedCount.get());
        System.out.println("만료 서비스 정상 종료: " + expireCompletedCount.get());
        System.out.println("Toss 승인 API 호출 횟수: " + confirmApiCount.get());
        System.out.println("실제 DB 결제 개수: " + getTestPaymentCount());
        System.out.println("실제 결제 처리 이력 개수: " + getTestOperationCount());
        System.out.println("만료 대상 주문 상태: " + expiredResult.getOrderStatus());
        System.out.println("정상 주문 상태: " + normalResult.getOrderStatus());
        System.out.println("처리 전 재고: " + beforeStock);
        System.out.println("실제 복구 재고: " + restoredStock);
        System.out.println("최종 재고: " + finalStock);
        System.out.println("활성 주문 수량 합계: " + activeQuantity);
        System.out.println("정상 주문 Redis 키 존재 여부: " + stringRedisTemplate.hasKey("order:pending:" + normalOrder.orderId()));
    }

    @Test
    @DisplayName("동일 결제 완료 주문을 동시에 취소해도 결제 취소 및 재고 복구는 한 번만 수행된다")
    void cancelPayment_concurrentDuplicate() throws Exception {

        int threadCount = 10;
        int initialStock = 10;
        int cancelQuantity = 2;
        int normalQuantity = 3;

        createProduct(initialStock, 10);

        User cancelUser = createUser();
        User normalUser = createUser();

        // 다른 회원의 정상 주문 생성
        OrderResponse normalOrder = orderService.createOrder(normalUser.getUsername(), createOrderRequest(normalQuantity));

        // 결제 취소 대상 주문 생성
        OrderResponse targetOrder = orderService.createOrder(cancelUser.getUsername(), createOrderRequest(cancelQuantity));

        Long targetOrderId = targetOrder.orderId();
        String paymentKey = createPaymentKey();

        // Toss 승인 및 취소 API 성공 응답 설정
        mockTossApiSuccess();

        PaymentRequest request = createPaymentRequest(targetOrder.tossOrderId(), paymentKey, targetOrder.totalPrice());

        // 실제 PaymentService를 통해 결제 완료
        PaymentResponse paidResponse = paymentService.confirmPayment(cancelUser.getUsername(), request);

        assertEquals(PaymentStatus.PAID, paidResponse.paymentStatus());
        assertEquals(1, confirmApiCount.get());

        Order paidOrder = orderRepository.findById(targetOrderId).orElseThrow();
        Payment paidPayment = paymentRepository.findByOrder_OrderId(targetOrderId).orElseThrow();

        assertEquals(OrderStatus.PAID, paidOrder.getOrderStatus());
        assertEquals(PaymentStatus.PAID, paidPayment.getPaymentStatus());

        Long originalPaymentId = paidPayment.getPaymentId();
        LocalDateTime originalPaidAt = paidPayment.getPaidAt();

        // 사전 결제 승인 처리 이력 검증
        List<PaymentOperation> beforeOperations = paymentOperationRepository.findAllByOrderId(targetOrderId);

        assertEquals(1, beforeOperations.size());
        assertEquals(PaymentOperationType.CONFIRM, beforeOperations.get(0).getOperationType());
        assertEquals(PaymentOperationStatus.COMPLETED, beforeOperations.get(0).getOperationStatus());

        Long originalConfirmOperationId = beforeOperations.get(0).getOperationId();

        // 초기 재고 10 - 정상 주문 3 - 결제 완료 주문 2
        int beforeStock = getCurrentStock();

        assertEquals(5, beforeStock);

        // 사전 결제 승인 호출 기록 제거
        // Mock 설정은 유지하여 이후 취소 요청만 검증
        clearInvocations(restClient);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        List<Runnable> tasks = new ArrayList<>();

        // 동일 결제 완료 주문의 취소 요청 10건 생성
        for (int i = 0; i < threadCount; i++) {

            tasks.add(() -> {
                try {
                    // 주문 비관적 락을 사용하는 실제 주문 취소 서비스 호출
                    OrderResponse response = orderService.cancelOrder(cancelUser.getUsername(), targetOrderId);

                    assertEquals(OrderStatus.CANCELLED, response.orderStatus());

                    successCount.incrementAndGet();

                } catch (OrderException e) {

                    // 이미 취소된 주문은 중복 취소 불가능
                    assertEquals(OrderErrorCode.ORDER_ALREADY_CANCELLED, e.getErrorCode());

                    rejectedCount.incrementAndGet();
                }
            });
        }

        executeConcurrently(tasks);

        // 결제 취소 1건만 성공
        assertEquals(1, successCount.get());
        assertEquals(9, rejectedCount.get());
        assertEquals(threadCount, successCount.get() + rejectedCount.get());

        // 사전 결제 승인 1회, 실제 취소 API 1회
        assertEquals(1, confirmApiCount.get());
        assertEquals(1, cancelApiCount.get());

        // 동시 취소 요청 중 외부 API 호출 한 번만 수행
        verify(restClient, times(1)).post();

        // 실제 DB 주문 상태 검증
        Order cancelledOrder = orderRepository.findById(targetOrderId).orElseThrow();

        assertEquals(OrderStatus.CANCELLED, cancelledOrder.getOrderStatus());
        assertNotNull(cancelledOrder.getPaymentAt());

        // 실제 DB 결제 상태 검증
        Payment cancelledPayment = paymentRepository.findByOrder_OrderId(targetOrderId).orElseThrow();

        assertEquals(originalPaymentId, cancelledPayment.getPaymentId());
        assertEquals(PaymentStatus.CANCELLED, cancelledPayment.getPaymentStatus());
        assertEquals(originalPaidAt, cancelledPayment.getPaidAt());
        assertEquals(paymentKey, cancelledPayment.getPaymentKey());
        assertEquals(targetOrder.totalPrice(), cancelledPayment.getAmount());
        assertNotNull(cancelledPayment.getCanceledAt());

        // 다른 정상 주문은 영향을 받지 않아야 함
        Order normalResult = orderRepository.findById(normalOrder.orderId()).orElseThrow();

        assertEquals(OrderStatus.PENDING, normalResult.getOrderStatus());

        // 취소 대상 주문 수량만 복구되어야 함
        int finalStock = getCurrentStock();
        int restoredStock = finalStock - beforeStock;

        assertEquals(cancelQuantity, restoredStock);
        assertEquals(7, finalStock);

        // 활성 주문 수량 검증
        int activeQuantity = getActivePurchasedQuantity();

        assertEquals(normalQuantity, activeQuantity);
        assertEquals(initialStock, finalStock + activeQuantity);

        // 실제 DB 주문 및 결제 데이터 유지 검증
        assertEquals(2, findTestOrders().size());
        assertEquals(1, getTestPaymentCount());

        // 실제 결제 승인 및 취소 처리 이력 검증
        List<PaymentOperation> operations = paymentOperationRepository.findAllByOrderId(targetOrderId);

        assertEquals(2, operations.size());
        assertEquals(2, getTestOperationCount());

        PaymentOperation confirmOperation = operations.stream()
                .filter(operation -> operation.getOperationType() == PaymentOperationType.CONFIRM)
                .findFirst()
                .orElseThrow();

        PaymentOperation cancelOperation = operations.stream()
                .filter(operation -> operation.getOperationType() == PaymentOperationType.CANCEL)
                .findFirst()
                .orElseThrow();

        // 기존 결제 승인 처리 이력 유지
        assertEquals(originalConfirmOperationId, confirmOperation.getOperationId());
        assertEquals(PaymentOperationStatus.COMPLETED, confirmOperation.getOperationStatus());
        assertEquals(targetOrderId, confirmOperation.getOrderId());
        assertEquals(targetOrder.tossOrderId(), confirmOperation.getTossOrderId());
        assertEquals(paymentKey, confirmOperation.getPaymentKey());
        assertEquals(targetOrder.totalPrice(), confirmOperation.getAmount());
        assertEquals("confirm-" + targetOrder.tossOrderId(), confirmOperation.getIdempotencyKey());

        // 결제 취소 처리 이력 한 건만 생성되었는지 검증
        assertNotNull(cancelOperation.getOperationId());
        assertNotEquals(confirmOperation.getOperationId(), cancelOperation.getOperationId());
        assertEquals(PaymentOperationStatus.COMPLETED, cancelOperation.getOperationStatus());
        assertEquals(targetOrderId, cancelOperation.getOrderId());
        assertEquals(targetOrder.tossOrderId(), cancelOperation.getTossOrderId());
        assertEquals(paymentKey, cancelOperation.getPaymentKey());
        assertEquals(targetOrder.totalPrice(), cancelOperation.getAmount());
        assertEquals("cancel-" + paymentKey, cancelOperation.getIdempotencyKey());

        // 다른 정상 주문의 결제 처리 이력은 존재하지 않아야 함
        assertTrue(paymentOperationRepository.findAllByOrderId(normalOrder.orderId()).isEmpty());

        // 정상 주문의 Redis 만료 키 유지
        assertTrue(Boolean.TRUE.equals(stringRedisTemplate.hasKey("order:pending:" + normalOrder.orderId())));

        // 결제 완료 후 삭제된 Redis 만료 키가 다시 생성되지 않아야 함
        assertFalse(Boolean.TRUE.equals(stringRedisTemplate.hasKey("order:pending:" + targetOrderId)));

        // 테스트 결과 출력
        System.out.println("===== 중복 결제 취소 방지 동시성 테스트 결과 =====");
        System.out.println("전체 취소 요청: " + threadCount);
        System.out.println("결제 취소 성공: " + successCount.get());
        System.out.println("결제 취소 거절: " + rejectedCount.get());
        System.out.println("Toss 승인 API 호출 횟수: " + confirmApiCount.get());
        System.out.println("Toss 취소 API 호출 횟수: " + cancelApiCount.get());
        System.out.println("실제 DB 결제 개수: " + getTestPaymentCount());
        System.out.println("실제 결제 처리 이력 개수: " + getTestOperationCount());
        System.out.println("승인 처리 이력 상태: " + confirmOperation.getOperationStatus());
        System.out.println("취소 처리 이력 상태: " + cancelOperation.getOperationStatus());
        System.out.println("최종 주문 상태: " + cancelledOrder.getOrderStatus());
        System.out.println("최종 결제 상태: " + cancelledPayment.getPaymentStatus());
        System.out.println("정상 주문 상태: " + normalResult.getOrderStatus());
        System.out.println("처리 전 재고: " + beforeStock);
        System.out.println("실제 복구 재고: " + restoredStock);
        System.out.println("최종 재고: " + finalStock);
        System.out.println("활성 주문 수량 합계: " + activeQuantity);
    }
}
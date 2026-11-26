package com.popq.platform.order.concurrency;

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
import com.popq.platform.order.service.OrderService;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OrderConcurrencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderExpirationService orderExpirationService;

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


    // 공통 테스트 데이터
    private static final int UNIT_PRICE = 10000;
    private static final List<OrderStatus> ACTIVE_STATUSES =
            List.of(
                    OrderStatus.PENDING,
                    OrderStatus.PAID
            );
    private final List<Long> userIds = new ArrayList<>();
    private Long popId;
    private Long prdId;
    private PopupStore popupStore;

    // 테스트 초기 설정 및 데이터 정리

    @BeforeEach
    void setUp() {

        userIds.clear();

        popId = null;
        prdId = null;

        popupStore = createPopupStore();
    }

    @AfterEach
    void tearDown() {

        // 테스트 주문 조회
        List<Order> orders = findTestOrders();

        // Redis 주문 만료 키 삭제
        for (Order order : orders) {
            stringRedisTemplate.delete("order:pending:" + order.getOrderId());
        }

        // 주문 삭제
        // CascadeType.ALL로 OrderItem도 함께 삭제
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
                .name("주문 테스트 회원")
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
                .title("주문 동시성 테스트 팝업")
                .location("테스트 장소")
                .startDate(today)
                .endDate(today.plusDays(7))
                .category("ETC")
                .description("주문 동시성 테스트용 팝업")
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
                .productName("동시성 테스트 상품")
                .price(UNIT_PRICE)
                .stock(stock)
                .maxQty(maxQty)
                .saleStart(LocalDateTime.now().minusDays(1))
                .saleEnd(LocalDateTime.now().plusDays(5))
                .category("ETC")
                .productImg("test.jpg")
                .productDesc("주문 동시성 테스트 상품")
                .build();

        Product savedProduct = productRepository.saveAndFlush(product);

        prdId = savedProduct.getPrdId();

        return savedProduct;
    }

    // 실제 주문 요청 DTO 생성
    private OrderCreateRequest createOrderRequest(int quantity) {

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

        OrderCreateRequest request = new OrderCreateRequest();

        ReflectionTestUtils.setField(
                request,
                "items",
                List.of(item)
        );

        ReflectionTestUtils.setField(
                request,
                "receiverName",
                "테스트 회원"
        );

        ReflectionTestUtils.setField(
                request,
                "receiverPhone",
                "01012345678"
        );

        ReflectionTestUtils.setField(
                request,
                "deliveryAddress",
                "테스트 주소"
        );

        ReflectionTestUtils.setField(
                request,
                "deliveryAddressDetail",
                "101호"
        );

        ReflectionTestUtils.setField(
                request,
                "deliveryMemo",
                "문 앞에 놓아주세요"
        );

        return request;
    }

    // 만료 시간이 지난 PENDING 주문 생성
    private Order createExpiredPendingOrder(User user, int quantity) {

        return transactionTemplate.execute(status -> {
            // 실제 상품 조회
            Product product = productRepository.findById(prdId).orElseThrow();
            // 실제 회원 조회
            User managedUser = userRepository.findById(user.getId()).orElseThrow();

            // 실제 DB 재고 차감
            int updatedRows = productRepository.decreaseStock(prdId, quantity);

            assertEquals(1, updatedRows);

            // 만료 시간이 지난 주문 생성
            Order order = Order.createOrder(
                    managedUser,
                    UNIT_PRICE * quantity,
                    LocalDateTime.now().minusMinutes(5),
                    "테스트 회원",
                    "01012345678",
                    "테스트 주소",
                    "101호",
                    "문 앞에 놓아주세요",
                    null
            );

            // 주문 생성 시각도 과거로 설정
            ReflectionTestUtils.setField(
                    order,
                    "orderedAt",
                    LocalDateTime.now().minusMinutes(15)
            );

            // 주문 상품 생성
            OrderItem orderItem = OrderItem.createOrderItem(
                    product,
                    quantity,
                    UNIT_PRICE,
                    UNIT_PRICE * quantity
            );

            order.addOrderItem(orderItem);

            // 주문 및 주문 상품 실제 DB 저장
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

        return productRepository.findById(prdId)
                .orElseThrow()
                .getStock();
    }

    // 실제 DB 활성 주문의 상품 수량 합계 조회
    private int getActivePurchasedQuantity() {

        return userIds.stream()
                .mapToInt(userId -> orderItemRepository.sumPurchasedQuantity(
                        userId,
                        prdId,
                        ACTIVE_STATUSES))
                .sum();
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

            assertTrue(executorService.awaitTermination(
                    10, TimeUnit.SECONDS), "스레드 종료 시간 초과"
            );
        }
    }

    @Test
    @DisplayName("여러 회원이 동시에 주문해도 상품 재고를 초과하여 판매되지 않는다")
    void createOrder_concurrentStock() throws Exception {

        int threadCount = 10;
        int initialStock = 5;
        int maxQty = 10;
        int quantity = 1;

        createProduct(initialStock, maxQty);

        assertEquals(initialStock, getCurrentStock());

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Runnable> tasks = new ArrayList<>();

        // 서로 다른 회원 10명 생성
        for (int i = 0; i < threadCount; i++) {
            User user = createUser();

            OrderCreateRequest request = createOrderRequest(quantity);

            tasks.add(() -> {
                try {
                    OrderResponse response = orderService.createOrder(user.getUsername(), request);

                    assertNotNull(response.orderId());

                    assertEquals(OrderStatus.PENDING, response.orderStatus());
                    assertEquals(UNIT_PRICE, response.totalPrice());

                    successCount.incrementAndGet();

                } catch (OrderException e) {

                    // 재고 부족 예외만 정상 실패로 집계
                    assertEquals(OrderErrorCode.INSUFFICIENT_STOCK, e.getErrorCode());

                    failCount.incrementAndGet();
                }
            });
        }

        executeConcurrently(tasks);

        assertEquals(5, successCount.get());
        assertEquals(5, failCount.get());

        assertEquals(threadCount, successCount.get() + failCount.get());

        // 실제 DB 재고 검증
        int finalStock = getCurrentStock();

        assertEquals(0, finalStock);
        assertTrue(finalStock >= 0);

        // 실제 DB 주문 검증
        List<Order> orders = findTestOrders();

        assertEquals(5, orders.size());

        assertTrue(orders.stream()
                .allMatch(order -> order.getOrderStatus() == OrderStatus.PENDING)
        );

        // 실제 활성 주문 수량 합계 검증
        int activeQuantity = getActivePurchasedQuantity();

        assertEquals(5, activeQuantity);

        // 초기 재고 = 남은 재고 + 활성 주문 수량
        assertEquals(initialStock, finalStock + activeQuantity);

        // 테스트 결과 출력
        System.out.println("===== 재고 초과 판매 방지 동시성 테스트 결과 =====");
        System.out.println("전체 요청: " + threadCount);
        System.out.println("주문 성공: " + successCount.get());
        System.out.println("주문 실패: " + failCount.get());
        System.out.println("초기 재고: " + initialStock);
        System.out.println("최종 재고: " + finalStock);
        System.out.println("실제 DB 주문 개수: " + orders.size());
        System.out.println("활성 주문 수량 합계: " + activeQuantity);
    }

    @Test
    @DisplayName("동일 회원이 동시에 주문해도 1인당 최대 구매 수량을 초과하지 않는다")
    void createOrder_concurrentPurchaseLimit() throws Exception {

        int threadCount = 10;
        int initialStock = 20;
        int maxQty = 3;
        int quantity = 1;

        // 재고는 충분하지만 구매 제한은 3개
        createProduct(initialStock, maxQty);

        User user = createUser();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Runnable> tasks = new ArrayList<>();

        // 동일 회원의 주문 요청 10건 생성
        for (int i = 0; i < threadCount; i++) {
            OrderCreateRequest request = createOrderRequest(quantity);

            tasks.add(() -> {
                try {
                    OrderResponse response = orderService.createOrder(user.getUsername(), request);

                    assertNotNull(response.orderId());

                    assertEquals(OrderStatus.PENDING, response.orderStatus());

                    successCount.incrementAndGet();

                } catch (OrderException e) {

                    // 구매 제한 초과 예외만 정상 실패로 집계
                    assertEquals(OrderErrorCode.PURCHASE_LIMIT_EXCEEDED, e.getErrorCode());

                    failCount.incrementAndGet();
                }
            });
        }

        executeConcurrently(tasks);

        assertEquals(3, successCount.get());
        assertEquals(7, failCount.get());

        assertEquals(threadCount, successCount.get() + failCount.get());

        // 실제 DB 주문 개수 검증
        List<Order> orders = findTestOrders();

        assertEquals(3, orders.size());

        assertTrue(orders.stream()
                .allMatch(order -> order.getOrderStatus() == OrderStatus.PENDING)
        );

        // 실제 DB 회원별 구매 수량 검증
        int purchasedQuantity = orderItemRepository.sumPurchasedQuantity(user.getId(), prdId, ACTIVE_STATUSES);

        assertEquals(maxQty, purchasedQuantity);

        assertTrue(purchasedQuantity <= maxQty);

        // 실제 DB 재고 검증
        int finalStock = getCurrentStock();

        assertEquals(initialStock - purchasedQuantity, finalStock);

        assertEquals(17, finalStock);

        // 테스트 결과 출력
        System.out.println("===== 최대 구매 수량 동시성 테스트 결과 =====");
        System.out.println("전체 요청: " + threadCount);
        System.out.println("주문 성공: " + successCount.get());
        System.out.println("주문 실패: " + failCount.get());
        System.out.println("최대 구매 수량: " + maxQty);
        System.out.println("실제 구매 수량: " + purchasedQuantity);
        System.out.println("초기 재고: " + initialStock);
        System.out.println("최종 재고: " + finalStock);
        System.out.println("실제 DB 주문 개수: " + orders.size());
    }

    @Test
    @DisplayName("주문 취소와 만료가 동시에 발생해도 재고가 중복 복구되지 않는다")
    void cancelAndExpire_concurrent() throws Exception {

        int initialStock = 10;
        int expiredQuantity = 2;
        int normalQuantity = 3;

        createProduct(initialStock, 10);

        User expiredUser = createUser();
        User normalUser = createUser();

        // 다른 회원의 정상 주문 3개 생성
        OrderResponse normalOrder = orderService.createOrder(normalUser.getUsername(), createOrderRequest(normalQuantity));

        // 만료 시간이 지난 PENDING 주문 2개 생성
        Order expiredOrder = createExpiredPendingOrder(
                expiredUser,
                expiredQuantity
        );

        Long expiredOrderId = expiredOrder.getOrderId();

        assertEquals(OrderStatus.PENDING, expiredOrder.getOrderStatus());
        assertEquals(OrderStatus.PENDING, normalOrder.orderStatus());

        // 초기 재고 10 - 정상 주문 3 - 만료 대상 2
        int beforeStock = getCurrentStock();

        assertEquals(5, beforeStock);

        AtomicInteger cancelSuccessCount = new AtomicInteger(0);
        AtomicInteger cancelRejectedCount = new AtomicInteger(0);
        AtomicInteger expireCompletedCount = new AtomicInteger(0);
        List<Runnable> tasks = new ArrayList<>();

        // Thread A: 주문 취소
        tasks.add(() -> {
            try {
                OrderResponse response = orderService.cancelOrder(expiredUser.getUsername(), expiredOrderId);

                assertEquals(OrderStatus.CANCELLED, response.orderStatus());

                cancelSuccessCount.incrementAndGet();

            } catch (OrderException e) {

                // 만료 처리가 먼저 실행된 경우
                assertEquals(OrderErrorCode.ORDER_EXPIRED, e.getErrorCode());

                cancelRejectedCount.incrementAndGet();
            }
        });

        // Thread B: 주문 만료
        tasks.add(() -> {
            orderExpirationService.expireOrder(expiredOrderId);

            expireCompletedCount.incrementAndGet();
        });

        executeConcurrently(tasks);

        // 취소 요청은 성공 또는 만료된 주문 예외
        assertEquals(1, cancelSuccessCount.get() + cancelRejectedCount.get());

        // 만료 서비스는 정상 종료
        assertEquals(1, expireCompletedCount.get());

        // 실제 DB 만료 대상 주문 재조회
        Order result = orderRepository.findById(expiredOrderId).orElseThrow();

        // 실행 순서에 따른 최종 상태 검증
        if (cancelSuccessCount.get() == 1) {
            assertEquals(OrderStatus.CANCELLED, result.getOrderStatus());

        } else {
            assertEquals(OrderStatus.EXPIRED, result.getOrderStatus());
        }

        // 다른 정상 주문은 영향을 받지 않아야 함
        Order normalResult = orderRepository.findById(normalOrder.orderId()).orElseThrow();

        assertEquals(OrderStatus.PENDING, normalResult.getOrderStatus());

        // 실제 재고 복구 검증
        int finalStock = getCurrentStock();

        int restoredStock = finalStock - beforeStock;

        // 대상 주문 수량 2개만 복구
        assertEquals(expiredQuantity, restoredStock);

        assertEquals(7, finalStock);

        // 활성 주문 수량 검증
        int activeQuantity = getActivePurchasedQuantity();

        assertEquals(normalQuantity, activeQuantity);
        assertEquals(initialStock, finalStock + activeQuantity);

        // 실제 DB 주문 데이터 유지 검증
        List<Order> orders = findTestOrders();

        assertEquals(2, orders.size());

        // 테스트 결과 출력
        System.out.println("===== 주문 취소 및 만료 동시성 테스트 결과 =====");
        System.out.println("전체 요청: 2");
        System.out.println("취소 성공: " + cancelSuccessCount.get());
        System.out.println("취소 거절: " + cancelRejectedCount.get());
        System.out.println("만료 서비스 정상 종료: " + expireCompletedCount.get());
        System.out.println("처리 전 재고: " + beforeStock);
        System.out.println("실제 복구 재고: " + restoredStock);
        System.out.println("최종 재고: " + finalStock);
        System.out.println("대상 주문 상태: " + result.getOrderStatus());
        System.out.println("정상 주문 상태: " + normalResult.getOrderStatus());
        System.out.println("실제 DB 주문 개수: " + orders.size());
        System.out.println("활성 주문 수량 합계: " + activeQuantity);
    }

    @Test
    @DisplayName("동일 주문을 동시에 만료 처리해도 상품 재고가 중복 복구되지 않는다")
    void expireOrder_concurrent() throws Exception {

        int threadCount = 10;
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

        AtomicInteger completedCount = new AtomicInteger(0);
        List<Runnable> tasks = new ArrayList<>();

        // 동일 주문의 만료 요청 10건 생성
        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                orderExpirationService.expireOrder(expiredOrderId);

                completedCount.incrementAndGet();
            });
        }

        executeConcurrently(tasks);

        // 모든 요청이 예외 없이 종료되어야 함
        assertEquals(threadCount, completedCount.get());

        // 실제 DB 만료 대상 주문 조회
        Order expiredResult = orderRepository.findById(expiredOrderId).orElseThrow();

        assertEquals(OrderStatus.EXPIRED, expiredResult.getOrderStatus());

        // 다른 회원의 정상 주문 조회
        Order normalResult = orderRepository.findById(normalOrder.orderId()).orElseThrow();

        assertEquals(OrderStatus.PENDING, normalResult.getOrderStatus());

        // 실제 DB 재고 검증
        int finalStock = getCurrentStock();
        int restoredStock = finalStock - beforeStock;

        // 만료 대상 수량 2개만 복구
        assertEquals(expiredQuantity, restoredStock);

        assertEquals(7, finalStock);

        // 실제 DB 전체 주문 검증
        List<Order> orders = findTestOrders();

        assertEquals(2, orders.size());

        long expiredCount = orders.stream()
                .filter(order -> order.getOrderStatus() == OrderStatus.EXPIRED)
                .count();

        long pendingCount = orders.stream()
                .filter(order -> order.getOrderStatus() == OrderStatus.PENDING)
                .count();

        assertEquals(1, expiredCount);
        assertEquals(1, pendingCount);

        // 정상 주문 수량 유지 검증
        int activeQuantity = getActivePurchasedQuantity();

        assertEquals(normalQuantity, activeQuantity);

        // 재고와 활성 주문 수량의 데이터 정합성 검증
        assertEquals(initialStock, finalStock + activeQuantity);

        // 테스트 결과 출력
        System.out.println("===== 중복 주문 만료 동시성 테스트 결과 =====");
        System.out.println("전체 만료 요청: " + threadCount);
        System.out.println("정상 종료 요청: " + completedCount.get());
        System.out.println("처리 전 재고: " + beforeStock);
        System.out.println("만료 대상 수량: " + expiredQuantity);
        System.out.println("실제 복구 재고: " + restoredStock);
        System.out.println("최종 재고: " + finalStock);
        System.out.println("만료 대상 주문 상태: " + expiredResult.getOrderStatus());
        System.out.println("정상 주문 상태: " + normalResult.getOrderStatus());
        System.out.println("실제 DB 주문 개수: " + orders.size());
        System.out.println("EXPIRED 주문 개수: " + expiredCount);
        System.out.println("PENDING 주문 개수: " + pendingCount);
        System.out.println("활성 주문 수량 합계: " + activeQuantity);
    }
}

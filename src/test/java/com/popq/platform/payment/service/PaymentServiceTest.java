package com.popq.platform.payment.service;

import com.popq.platform.auth.entity.User;
import com.popq.platform.common.redis.RedisExpirationKeyService;
import com.popq.platform.coupon.entity.UserCoupon;
import com.popq.platform.coupon.service.CouponService;
import com.popq.platform.order.entity.Order;
import com.popq.platform.order.entity.OrderStatus;
import com.popq.platform.order.exception.OrderErrorCode;
import com.popq.platform.order.exception.OrderException;
import com.popq.platform.order.repository.OrderRepository;
import com.popq.platform.payment.dto.PaymentRequest;
import com.popq.platform.payment.dto.PaymentResponse;
import com.popq.platform.payment.dto.TossPaymentResponse;
import com.popq.platform.payment.entity.Payment;
import com.popq.platform.payment.entity.PaymentStatus;
import com.popq.platform.payment.entity.PaymentOperation;
import com.popq.platform.payment.entity.PaymentOperationType;
import com.popq.platform.payment.entity.PaymentOperationStatus;
import com.popq.platform.payment.exception.PaymentErrorCode;
import com.popq.platform.payment.exception.PaymentException;
import com.popq.platform.payment.repository.PaymentRepository;
import com.popq.platform.payment.repository.PaymentOperationRepository;
import com.popq.platform.product.repository.ProductRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RedisExpirationKeyService redisExpirationKeyService;

    @Mock
    private CouponService couponService;

    @Mock
    private PaymentOperationService paymentOperationService;

    @Mock
    private PaymentOperationRepository paymentOperationRepository;

    @Mock
    private ProductRepository productRepository;

    private PaymentService paymentService;
    private MockRestServiceServer mockServer;


    // ==================== 공통 테스트 데이터 ====================

    private static final String USERNAME = "testUser";

    private static final Long ORDER_ID = 1L;
    private static final Long PAYMENT_ID = 2L;
    private static final Long COUPON_ID = 3L;
    private static final Long OPERATION_ID = 4L;

    private static final String TOSS_ORDER_ID = "test-toss-order-id";
    private static final String PAYMENT_KEY = "test-payment-key";
    private static final String SECRET_KEY = "test-secret-key";

    private static final int AMOUNT = 30000;

    private static final String REDIS_KEY = "order:pending:" + ORDER_ID;

    private static final String CONFIRM_URL =
            "https://api.tosspayments.com/v1/payments/confirm";

    private static final String CANCEL_URL =
            "https://api.tosspayments.com/v1/payments/" + PAYMENT_KEY + "/cancel";

    private static final String LOOKUP_URL =
            "https://api.tosspayments.com/v1/payments/" + PAYMENT_KEY;

    private static final String CONFIRM_IDEMPOTENCY_KEY =
            "confirm-" + TOSS_ORDER_ID;

    private static final String CANCEL_IDEMPOTENCY_KEY =
            "cancel-" + PAYMENT_KEY;

    private static final String BASIC_AUTH =
            "Basic " + Base64.getEncoder()
                    .encodeToString(
                            (SECRET_KEY + ":").getBytes(StandardCharsets.UTF_8)
                    );


    // 테스트 초기 설정

    @BeforeEach
    void setUp() {

        RestClient.Builder builder = RestClient.builder();

        mockServer = MockRestServiceServer.bindTo(builder).build();

        RestClient restClient = builder.build();

        paymentService = new PaymentService(
                paymentRepository,
                orderRepository,
                redisExpirationKeyService,
                restClient,
                couponService,
                paymentOperationService,
                paymentOperationRepository,
                productRepository
        );

        ReflectionTestUtils.setField(
                paymentService, "tossSecretKey", SECRET_KEY
        );

        // 단위 테스트에서 트랜잭션 동기화 콜백 검증
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {

        try {
            mockServer.verify();

        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }


    // 공통 테스트 데이터 생성

    private User createUser() {

        return User.builder()
                .username(USERNAME)
                .password("testPassword")
                .name("테스트 회원")
                .phone("01012345678")
                .build();
    }

    private PaymentRequest createRequest(int amount) {

        PaymentRequest request = new PaymentRequest();

        ReflectionTestUtils.setField(request, "orderId", TOSS_ORDER_ID);
        ReflectionTestUtils.setField(request, "paymentKey", PAYMENT_KEY);
        ReflectionTestUtils.setField(request, "amount", amount);

        return request;
    }

    private Order createOrder(
            OrderStatus status,
            LocalDateTime expiresAt,
            UserCoupon userCoupon
    ) {

        Order order = Order.createOrder(
                createUser(),
                AMOUNT,
                expiresAt,
                "테스트 회원",
                "01012345678",
                "테스트 주소",
                "101호",
                "문 앞에 놓아주세요",
                userCoupon
        );

        ReflectionTestUtils.setField(order, "orderId", ORDER_ID);
        ReflectionTestUtils.setField(order, "tossOrderId", TOSS_ORDER_ID);

        switch (status) {

            case PAID -> order.completePayment();

            case CANCELLED -> order.cancel();

            case EXPIRED -> order.expire();

            default -> {
                // PENDING 상태 유지
            }
        }

        return order;
    }

    private Order createPendingOrder() {

        return createOrder(
                OrderStatus.PENDING,
                LocalDateTime.now().plusMinutes(10),
                null
        );
    }

    private Order createPaidOrder() {

        return createOrder(
                OrderStatus.PAID,
                LocalDateTime.now().plusMinutes(10),
                null
        );
    }

    private Payment createPayment(Order order, PaymentStatus status) {

        Payment payment = Payment.create(
                order, PAYMENT_KEY, AMOUNT
        );

        ReflectionTestUtils.setField(payment, "paymentId", PAYMENT_ID);

        if (status == PaymentStatus.CANCELLED) {
            payment.cancel();
        }

        return payment;
    }

    private PaymentOperation createConfirmOperation() {

        PaymentOperation operation = PaymentOperation.create(
                ORDER_ID,
                TOSS_ORDER_ID,
                PAYMENT_KEY,
                AMOUNT,
                CONFIRM_IDEMPOTENCY_KEY,
                PaymentOperationType.CONFIRM
        );

        ReflectionTestUtils.setField(
                operation, "operationId", OPERATION_ID
        );

        return operation;
    }


    // Repository Mock 설정

    private void mockOrderLookup(Order order) {

        when(orderRepository.findByTossOrderIdForUpdate(TOSS_ORDER_ID))
                .thenReturn(Optional.of(order));
    }

    private void mockPaymentLookup(Payment payment) {

        when(paymentRepository.findByOrder_OrderId(ORDER_ID))
                .thenReturn(Optional.of(payment));
    }

    private void mockRecoveryLookup(
            Order order, PaymentOperation operation
    ) {

        when(orderRepository.findByIdForUpdate(ORDER_ID))
                .thenReturn(Optional.of(order));

        when(paymentOperationRepository.findByIdForUpdate(OPERATION_ID))
                .thenReturn(Optional.of(operation));
    }

    // DB 결제 ID 할당 동작 모방
    private void mockPaymentSave() {

        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> {

                    Payment payment = invocation.getArgument(0);

                    ReflectionTestUtils.setField(
                            payment, "paymentId", PAYMENT_ID
                    );

                    return payment;
                });
    }

    private void mockConfirmOperation() {

        when(paymentOperationService.createOperation(
                ORDER_ID,
                TOSS_ORDER_ID,
                PAYMENT_KEY,
                AMOUNT,
                CONFIRM_IDEMPOTENCY_KEY,
                PaymentOperationType.CONFIRM
        )).thenReturn(OPERATION_ID);
    }

    private void mockCancelOperation() {

        when(paymentOperationService.createOperation(
                ORDER_ID,
                TOSS_ORDER_ID,
                PAYMENT_KEY,
                AMOUNT,
                CANCEL_IDEMPOTENCY_KEY,
                PaymentOperationType.CANCEL
        )).thenReturn(OPERATION_ID);
    }

    // DB 커밋 이후 콜백 실행 모방
    private void executeAfterCommit() {

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
    }


    // Toss API Mock 설정

    private void expectConfirmRequest(ResponseCreator responseCreator) {

        mockServer.expect(requestTo(CONFIRM_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, BASIC_AUTH))
                .andExpect(header("Idempotency-Key", CONFIRM_IDEMPOTENCY_KEY))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.paymentKey").value(PAYMENT_KEY))
                .andExpect(jsonPath("$.orderId").value(TOSS_ORDER_ID))
                .andExpect(jsonPath("$.amount").value(AMOUNT))
                .andRespond(responseCreator);
    }

    private void expectCancelRequest(ResponseCreator responseCreator) {

        mockServer.expect(requestTo(CANCEL_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, BASIC_AUTH))
                .andExpect(header("Idempotency-Key", CANCEL_IDEMPOTENCY_KEY))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.cancelReason").value("고객 요청"))
                .andRespond(responseCreator);
    }

    private void expectLookupRequest(ResponseCreator responseCreator) {

        mockServer.expect(requestTo(LOOKUP_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, BASIC_AUTH))
                .andRespond(responseCreator);
    }

    private void expectLookupSuccess(
            String status, String tossOrderId, int balanceAmount
    ) {

        String responseBody = """
                {
                    "paymentKey": "%s",
                    "orderId": "%s",
                    "status": "%s",
                    "totalAmount": %d,
                    "balanceAmount": %d,
                    "approvedAt": "2026-11-26T15:00:00+09:00"
                }
                """.formatted(
                PAYMENT_KEY,
                tossOrderId,
                status,
                AMOUNT,
                balanceAmount
        );

        expectLookupRequest(
                withSuccess(responseBody, MediaType.APPLICATION_JSON)
        );
    }

    private void verifyConfirmNotExecuted() {

        verifyNoInteractions(
                paymentRepository,
                couponService,
                redisExpirationKeyService
        );
    }


    // Toss API 오류 설정

    private enum TossFailureType {

        CLIENT_ERROR,
        SERVER_ERROR,
        CONNECTION_ERROR
    }

    private ResponseCreator createFailureResponse(
            TossFailureType failureType
    ) {

        return switch (failureType) {

            case CLIENT_ERROR -> withStatus(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {
                                "code": "TEST_CLIENT_ERROR",
                                "message": "요청 오류"
                            }
                            """);

            case SERVER_ERROR ->
                    withStatus(HttpStatus.INTERNAL_SERVER_ERROR);

            case CONNECTION_ERROR ->
                    withException(new IOException("Connection failed"));
        };
    }

    private PaymentErrorCode getConfirmErrorCode(
            TossFailureType failureType
    ) {

        return switch (failureType) {

            case CLIENT_ERROR -> PaymentErrorCode.TOSS_PAYMENT_FAILED;

            case SERVER_ERROR -> PaymentErrorCode.TOSS_SERVER_ERROR;

            case CONNECTION_ERROR ->
                    PaymentErrorCode.PAYMENT_CONNECTION_ERROR;
        };
    }

    private PaymentErrorCode getCancelErrorCode(
            TossFailureType failureType
    ) {

        return switch (failureType) {

            case CLIENT_ERROR -> PaymentErrorCode.TOSS_CANCEL_FAILED;

            case SERVER_ERROR -> PaymentErrorCode.TOSS_SERVER_ERROR;

            case CONNECTION_ERROR ->
                    PaymentErrorCode.PAYMENT_CONNECTION_ERROR;
        };
    }

    @Test
    @DisplayName("존재하지 않는 주문은 결제할 수 없다")
    void confirmPayment_orderNotFound() {

        PaymentRequest request = createRequest(AMOUNT);

        when(orderRepository.findByTossOrderIdForUpdate(TOSS_ORDER_ID))
                .thenReturn(Optional.empty());

        OrderException exception = assertThrows(
                OrderException.class,
                () -> paymentService.confirmPayment(USERNAME, request)
        );

        assertEquals(
                OrderErrorCode.ORDER_NOT_FOUND,
                exception.getErrorCode()
        );

        verify(orderRepository)
                .findByTossOrderIdForUpdate(TOSS_ORDER_ID);

        verifyConfirmNotExecuted();

        verifyNoInteractions(paymentOperationService);
    }

    @ParameterizedTest
    @EnumSource(
            value = OrderStatus.class,
            names = {"CANCELLED", "EXPIRED"}
    )
    @DisplayName("취소되거나 만료된 주문은 결제할 수 없다")
    void confirmPayment_invalidOrderStatus(OrderStatus status) {

        Order order = createOrder(
                status, LocalDateTime.now().plusMinutes(10), null
        );

        PaymentRequest request = createRequest(AMOUNT);

        mockOrderLookup(order);

        OrderException exception = assertThrows(
                OrderException.class,
                () -> paymentService.confirmPayment(USERNAME, request)
        );

        assertEquals(
                OrderErrorCode.INVALID_ORDER_STATUS,
                exception.getErrorCode()
        );

        assertEquals(status, order.getOrderStatus());

        verifyConfirmNotExecuted();

        verifyNoInteractions(paymentOperationService);
    }

    @Test
    @DisplayName("만료 시간이 지난 PENDING 주문은 결제할 수 없다")
    void confirmPayment_orderExpired() {

        Order order = createOrder(
                OrderStatus.PENDING,
                LocalDateTime.now().minusMinutes(5),
                null
        );

        PaymentRequest request = createRequest(AMOUNT);

        mockOrderLookup(order);

        OrderException exception = assertThrows(
                OrderException.class,
                () -> paymentService.confirmPayment(USERNAME, request)
        );

        assertEquals(
                OrderErrorCode.ORDER_EXPIRED,
                exception.getErrorCode()
        );

        assertEquals(OrderStatus.PENDING, order.getOrderStatus());
        assertNull(order.getPaymentAt());

        verifyConfirmNotExecuted();

        verifyNoInteractions(paymentOperationService);
    }

    @Test
    @DisplayName("주문 금액과 결제 요청 금액이 다르면 승인할 수 없다")
    void confirmPayment_amountMismatch() {

        Order order = createPendingOrder();

        PaymentRequest request = createRequest(20000);

        mockOrderLookup(order);

        PaymentException exception = assertThrows(
                PaymentException.class,
                () -> paymentService.confirmPayment(USERNAME, request)
        );

        assertEquals(
                PaymentErrorCode.AMOUNT_MISMATCH,
                exception.getErrorCode()
        );

        assertEquals(OrderStatus.PENDING, order.getOrderStatus());
        assertNull(order.getPaymentAt());

        verifyConfirmNotExecuted();

        verifyNoInteractions(paymentOperationService);
    }

    @Test
    @DisplayName("결제 승인 성공 시 결제 정보 저장 및 주문 상태 변경이 수행된다")
    void confirmPayment_success() {

        Order order = createPendingOrder();

        PaymentRequest request = createRequest(AMOUNT);

        mockOrderLookup(order);
        mockConfirmOperation();
        mockPaymentSave();

        expectConfirmRequest(withSuccess());

        LocalDateTime before = LocalDateTime.now();

        PaymentResponse response = paymentService.confirmPayment(
                USERNAME, request
        );

        LocalDateTime after = LocalDateTime.now();

        // 결제 응답 검증
        assertEquals(PAYMENT_ID, response.paymentId());
        assertEquals(ORDER_ID, response.orderId());
        assertEquals(PAYMENT_KEY, response.paymentKey());
        assertEquals(AMOUNT, response.amount());
        assertEquals(PaymentStatus.PAID, response.paymentStatus());

        assertNotNull(response.paidAt());
        assertNull(response.canceledAt());

        assertFalse(response.paidAt().isBefore(before));
        assertFalse(response.paidAt().isAfter(after));

        // 커밋 전 Redis 키 삭제 방지
        verifyNoInteractions(redisExpirationKeyService);

        executeAfterCommit();

        ArgumentCaptor<Payment> paymentCaptor =
                ArgumentCaptor.forClass(Payment.class);

        InOrder inOrder = inOrder(
                orderRepository,
                paymentOperationService,
                paymentRepository,
                redisExpirationKeyService
        );

        inOrder.verify(orderRepository)
                .findByTossOrderIdForUpdate(TOSS_ORDER_ID);

        inOrder.verify(paymentOperationService)
                .existsOperation(CONFIRM_IDEMPOTENCY_KEY);

        inOrder.verify(paymentOperationService).createOperation(
                ORDER_ID,
                TOSS_ORDER_ID,
                PAYMENT_KEY,
                AMOUNT,
                CONFIRM_IDEMPOTENCY_KEY,
                PaymentOperationType.CONFIRM
        );

        inOrder.verify(paymentRepository)
                .save(paymentCaptor.capture());

        inOrder.verify(paymentOperationService)
                .completeOperation(OPERATION_ID);

        inOrder.verify(redisExpirationKeyService)
                .delete(REDIS_KEY);

        Payment savedPayment = paymentCaptor.getValue();

        assertSame(order, savedPayment.getOrder());
        assertEquals(PAYMENT_KEY, savedPayment.getPaymentKey());
        assertEquals(AMOUNT, savedPayment.getAmount());
        assertEquals(PaymentStatus.PAID, savedPayment.getPaymentStatus());

        assertNotNull(savedPayment.getPaidAt());
        assertNull(savedPayment.getCanceledAt());

        // 주문 상태 변경 검증
        assertEquals(OrderStatus.PAID, order.getOrderStatus());
        assertNotNull(order.getPaymentAt());

        assertFalse(order.getPaymentAt().isBefore(before));
        assertFalse(order.getPaymentAt().isAfter(after));

        assertEquals(order.getTotalPrice(), savedPayment.getAmount());

        verifyNoInteractions(couponService);

        // Dirty Checking 방식이므로 별도 주문 저장 없음
        verify(orderRepository, never()).save(any(Order.class));

        verifyNoMoreInteractions(paymentRepository);
    }

    @Test
    @DisplayName("쿠폰이 적용된 주문을 결제하면 쿠폰 사용 처리가 수행된다")
    void confirmPayment_withCoupon() {

        UserCoupon userCoupon = mock(UserCoupon.class);

        when(userCoupon.getId()).thenReturn(COUPON_ID);

        Order order = createOrder(
                OrderStatus.PENDING,
                LocalDateTime.now().plusMinutes(10),
                userCoupon
        );

        PaymentRequest request = createRequest(AMOUNT);

        mockOrderLookup(order);
        mockConfirmOperation();
        mockPaymentSave();

        expectConfirmRequest(withSuccess());

        PaymentResponse response = paymentService.confirmPayment(
                USERNAME, request
        );

        assertEquals(PaymentStatus.PAID, response.paymentStatus());
        assertEquals(OrderStatus.PAID, order.getOrderStatus());
        assertEquals(AMOUNT, response.amount());

        verifyNoInteractions(redisExpirationKeyService);

        executeAfterCommit();

        InOrder inOrder = inOrder(
                paymentRepository,
                couponService,
                paymentOperationService,
                redisExpirationKeyService
        );

        inOrder.verify(paymentRepository).save(any(Payment.class));

        inOrder.verify(couponService).useCoupon(USERNAME, COUPON_ID);

        inOrder.verify(paymentOperationService)
                .completeOperation(OPERATION_ID);

        inOrder.verify(redisExpirationKeyService).delete(REDIS_KEY);

        verifyNoMoreInteractions(couponService);
    }

    @ParameterizedTest
    @EnumSource(TossFailureType.class)
    @DisplayName("Toss 결제 승인 실패 시 적절한 예외가 발생하고 후속 처리를 수행하지 않는다")
    void confirmPayment_tossApiFailed(TossFailureType failureType) {

        Order order = createPendingOrder();

        PaymentRequest request = createRequest(AMOUNT);

        mockOrderLookup(order);
        mockConfirmOperation();

        expectConfirmRequest(createFailureResponse(failureType));

        PaymentException exception = assertThrows(
                PaymentException.class,
                () -> paymentService.confirmPayment(USERNAME, request)
        );

        assertEquals(
                getConfirmErrorCode(failureType),
                exception.getErrorCode()
        );

        assertEquals(OrderStatus.PENDING, order.getOrderStatus());
        assertNull(order.getPaymentAt());

        verify(paymentOperationService).createOperation(
                ORDER_ID,
                TOSS_ORDER_ID,
                PAYMENT_KEY,
                AMOUNT,
                CONFIRM_IDEMPOTENCY_KEY,
                PaymentOperationType.CONFIRM
        );

        verify(paymentOperationService, never())
                .completeOperation(anyLong());

        verifyConfirmNotExecuted();

        assertTrue(
                TransactionSynchronizationManager.getSynchronizations()
                        .isEmpty()
        );
    }

    @Test
    @DisplayName("존재하지 않는 결제 정보는 취소할 수 없다")
    void cancelPayment_paymentNotFound() {

        Order order = createPaidOrder();

        when(paymentRepository.findByOrder_OrderId(ORDER_ID))
                .thenReturn(Optional.empty());

        PaymentException exception = assertThrows(
                PaymentException.class,
                () -> paymentService.cancelPayment(order)
        );

        assertEquals(
                PaymentErrorCode.PAYMENT_NOT_FOUND,
                exception.getErrorCode()
        );

        assertEquals(OrderStatus.PAID, order.getOrderStatus());

        verify(paymentRepository).findByOrder_OrderId(ORDER_ID);

        verifyNoMoreInteractions(paymentRepository);

        verifyNoInteractions(
                orderRepository,
                couponService,
                redisExpirationKeyService,
                paymentOperationService
        );
    }

    @Test
    @DisplayName("이미 취소된 결제는 중복 취소할 수 없다")
    void cancelPayment_alreadyCancelled() {

        Order order = createPaidOrder();

        Payment payment = createPayment(
                order, PaymentStatus.CANCELLED
        );

        LocalDateTime originalCanceledAt = payment.getCanceledAt();

        mockPaymentLookup(payment);

        PaymentException exception = assertThrows(
                PaymentException.class,
                () -> paymentService.cancelPayment(order)
        );

        assertEquals(
                PaymentErrorCode.INVALID_PAYMENT_STATUS,
                exception.getErrorCode()
        );

        assertEquals(PaymentStatus.CANCELLED, payment.getPaymentStatus());

        assertEquals(originalCanceledAt, payment.getCanceledAt());

        verify(paymentRepository).findByOrder_OrderId(ORDER_ID);

        verifyNoMoreInteractions(paymentRepository);

        verifyNoInteractions(
                orderRepository,
                couponService,
                redisExpirationKeyService,
                paymentOperationService
        );
    }

    @Test
    @DisplayName("정상 결제 취소 시 결제 상태와 취소 시간이 변경된다")
    void cancelPayment_success() {

        Order order = createPaidOrder();

        Payment payment = createPayment(order, PaymentStatus.PAID);

        LocalDateTime originalPaidAt = payment.getPaidAt();

        mockPaymentLookup(payment);
        mockCancelOperation();

        expectCancelRequest(withSuccess());

        LocalDateTime before = LocalDateTime.now();

        paymentService.cancelPayment(order);

        LocalDateTime after = LocalDateTime.now();

        assertEquals(PaymentStatus.CANCELLED, payment.getPaymentStatus());

        assertNotNull(payment.getCanceledAt());

        assertFalse(payment.getCanceledAt().isBefore(before));
        assertFalse(payment.getCanceledAt().isAfter(after));

        assertEquals(originalPaidAt, payment.getPaidAt());
        assertEquals(PAYMENT_KEY, payment.getPaymentKey());
        assertEquals(AMOUNT, payment.getAmount());

        // 주문 상태 변경은 OrderService에서 수행
        assertEquals(OrderStatus.PAID, order.getOrderStatus());

        InOrder inOrder = inOrder(
                paymentRepository, paymentOperationService
        );

        inOrder.verify(paymentRepository)
                .findByOrder_OrderId(ORDER_ID);

        inOrder.verify(paymentOperationService)
                .existsOperation(CANCEL_IDEMPOTENCY_KEY);

        inOrder.verify(paymentOperationService).createOperation(
                ORDER_ID,
                TOSS_ORDER_ID,
                PAYMENT_KEY,
                AMOUNT,
                CANCEL_IDEMPOTENCY_KEY,
                PaymentOperationType.CANCEL
        );

        inOrder.verify(paymentOperationService)
                .completeOperation(OPERATION_ID);

        verify(paymentRepository, never()).save(any(Payment.class));

        verifyNoMoreInteractions(paymentRepository);

        verifyNoInteractions(
                orderRepository,
                couponService,
                redisExpirationKeyService
        );
    }

    @ParameterizedTest
    @EnumSource(TossFailureType.class)
    @DisplayName("Toss 결제 취소 실패 시 기존 결제 상태를 유지한다")
    void cancelPayment_tossApiFailed(TossFailureType failureType) {

        Order order = createPaidOrder();

        Payment payment = createPayment(order, PaymentStatus.PAID);

        LocalDateTime originalPaidAt = payment.getPaidAt();

        mockPaymentLookup(payment);
        mockCancelOperation();

        expectCancelRequest(createFailureResponse(failureType));

        PaymentException exception = assertThrows(
                PaymentException.class,
                () -> paymentService.cancelPayment(order)
        );

        assertEquals(
                getCancelErrorCode(failureType),
                exception.getErrorCode()
        );

        assertEquals(PaymentStatus.PAID, payment.getPaymentStatus());
        assertNull(payment.getCanceledAt());
        assertEquals(originalPaidAt, payment.getPaidAt());

        assertEquals(OrderStatus.PAID, order.getOrderStatus());

        verify(paymentRepository).findByOrder_OrderId(ORDER_ID);

        verify(paymentOperationService).createOperation(
                ORDER_ID,
                TOSS_ORDER_ID,
                PAYMENT_KEY,
                AMOUNT,
                CANCEL_IDEMPOTENCY_KEY,
                PaymentOperationType.CANCEL
        );

        verify(paymentOperationService, never())
                .completeOperation(anyLong());

        verifyNoMoreInteractions(paymentRepository);

        verifyNoInteractions(
                orderRepository,
                couponService,
                redisExpirationKeyService
        );
    }

    @Test
    @DisplayName("다른 회원의 주문에 대한 결제 승인 요청은 거절된다")
    void confirmPayment_accessDenied() {

        Order order = createPendingOrder();

        PaymentRequest request = createRequest(AMOUNT);

        mockOrderLookup(order);

        OrderException exception = assertThrows(
                OrderException.class,
                () -> paymentService.confirmPayment("otherUser", request)
        );

        assertEquals(
                OrderErrorCode.ORDER_ACCESS_DENIED,
                exception.getErrorCode()
        );

        assertEquals(OrderStatus.PENDING, order.getOrderStatus());

        verifyConfirmNotExecuted();

        verifyNoInteractions(paymentOperationService);
    }

    @Test
    @DisplayName("동일한 결제 승인 재요청 시 기존 결제 정보를 반환한다")
    void confirmPayment_idempotentSuccess() {

        Order order = createPaidOrder();

        Payment payment = createPayment(order, PaymentStatus.PAID);

        PaymentRequest request = createRequest(AMOUNT);

        mockOrderLookup(order);
        mockPaymentLookup(payment);

        PaymentResponse response = paymentService.confirmPayment(
                USERNAME, request
        );

        assertEquals(PAYMENT_ID, response.paymentId());
        assertEquals(ORDER_ID, response.orderId());
        assertEquals(PAYMENT_KEY, response.paymentKey());
        assertEquals(AMOUNT, response.amount());
        assertEquals(PaymentStatus.PAID, response.paymentStatus());

        assertEquals(payment.getPaidAt(), response.paidAt());

        verify(paymentRepository).findByOrder_OrderId(ORDER_ID);

        verify(paymentRepository, never()).save(any(Payment.class));

        verifyNoInteractions(
                paymentOperationService,
                couponService,
                redisExpirationKeyService
        );

        assertTrue(
                TransactionSynchronizationManager.getSynchronizations()
                        .isEmpty()
        );
    }

    @Test
    @DisplayName("기존 결제 키와 다른 결제 키로 재요청하면 거절된다")
    void confirmPayment_existingPaymentKeyMismatch() {

        Order order = createPaidOrder();

        Payment payment = createPayment(order, PaymentStatus.PAID);

        PaymentRequest request = createRequest(AMOUNT);

        ReflectionTestUtils.setField(
                request, "paymentKey", "other-payment-key"
        );

        mockOrderLookup(order);
        mockPaymentLookup(payment);

        PaymentException exception = assertThrows(
                PaymentException.class,
                () -> paymentService.confirmPayment(USERNAME, request)
        );

        assertEquals(
                PaymentErrorCode.PAYMENT_KEY_MISMATCH,
                exception.getErrorCode()
        );

        assertEquals(PaymentStatus.PAID, payment.getPaymentStatus());
        assertEquals(PAYMENT_KEY, payment.getPaymentKey());

        verify(paymentRepository, never()).save(any(Payment.class));

        verifyNoInteractions(
                paymentOperationService,
                couponService,
                redisExpirationKeyService
        );
    }

    @Test
    @DisplayName("기존 결제 금액과 다른 금액으로 재요청하면 거절된다")
    void confirmPayment_existingAmountMismatch() {

        Order order = createPaidOrder();

        Payment payment = createPayment(order, PaymentStatus.PAID);

        PaymentRequest request = createRequest(20000);

        mockOrderLookup(order);
        mockPaymentLookup(payment);

        PaymentException exception = assertThrows(
                PaymentException.class,
                () -> paymentService.confirmPayment(USERNAME, request)
        );

        assertEquals(
                PaymentErrorCode.AMOUNT_MISMATCH,
                exception.getErrorCode()
        );

        assertEquals(AMOUNT, payment.getAmount());
        assertEquals(PaymentStatus.PAID, payment.getPaymentStatus());

        verify(paymentRepository, never()).save(any(Payment.class));

        verifyNoInteractions(
                paymentOperationService,
                couponService,
                redisExpirationKeyService
        );
    }

    @Test
    @DisplayName("결제 완료 주문에 기존 결제 정보가 없으면 예외가 발생한다")
    void confirmPayment_existingPaymentNotFound() {

        Order order = createPaidOrder();

        PaymentRequest request = createRequest(AMOUNT);

        mockOrderLookup(order);

        when(paymentRepository.findByOrder_OrderId(ORDER_ID))
                .thenReturn(Optional.empty());

        PaymentException exception = assertThrows(
                PaymentException.class,
                () -> paymentService.confirmPayment(USERNAME, request)
        );

        assertEquals(
                PaymentErrorCode.PAYMENT_NOT_FOUND,
                exception.getErrorCode()
        );

        verify(paymentRepository, never()).save(any(Payment.class));

        verifyNoInteractions(
                paymentOperationService,
                couponService,
                redisExpirationKeyService
        );
    }

    @Test
    @DisplayName("기존 결제 상태가 PAID가 아니면 결제 승인 재요청을 거절한다")
    void confirmPayment_existingPaymentInvalidStatus() {

        Order order = createPaidOrder();

        Payment payment = createPayment(
                order, PaymentStatus.CANCELLED
        );

        PaymentRequest request = createRequest(AMOUNT);

        mockOrderLookup(order);
        mockPaymentLookup(payment);

        PaymentException exception = assertThrows(
                PaymentException.class,
                () -> paymentService.confirmPayment(USERNAME, request)
        );

        assertEquals(
                PaymentErrorCode.INVALID_PAYMENT_STATUS,
                exception.getErrorCode()
        );

        assertEquals(PaymentStatus.CANCELLED, payment.getPaymentStatus());

        verify(paymentRepository, never()).save(any(Payment.class));

        verifyNoInteractions(
                paymentOperationService,
                couponService,
                redisExpirationKeyService
        );
    }

    @Test
    @DisplayName("기존 결제 승인 처리 이력이 존재하면 중복 승인 요청을 거절한다")
    void confirmPayment_operationAlreadyExists() {

        Order order = createPendingOrder();

        PaymentRequest request = createRequest(AMOUNT);

        mockOrderLookup(order);

        when(paymentOperationService.existsOperation(
                CONFIRM_IDEMPOTENCY_KEY
        )).thenReturn(true);

        OrderException exception = assertThrows(
                OrderException.class,
                () -> paymentService.confirmPayment(USERNAME, request)
        );

        assertEquals(
                OrderErrorCode.PAYMENT_PROCESSING,
                exception.getErrorCode()
        );

        assertEquals(OrderStatus.PENDING, order.getOrderStatus());

        verify(paymentOperationService)
                .existsOperation(CONFIRM_IDEMPOTENCY_KEY);

        verifyNoMoreInteractions(paymentOperationService);

        verifyConfirmNotExecuted();
    }

    @Test
    @DisplayName("기존 결제 취소 처리 이력이 존재하면 중복 취소 요청을 거절한다")
    void cancelPayment_operationAlreadyExists() {

        Order order = createPaidOrder();

        Payment payment = createPayment(order, PaymentStatus.PAID);

        mockPaymentLookup(payment);

        when(paymentOperationService.existsOperation(
                CANCEL_IDEMPOTENCY_KEY
        )).thenReturn(true);

        OrderException exception = assertThrows(
                OrderException.class,
                () -> paymentService.cancelPayment(order)
        );

        assertEquals(
                OrderErrorCode.PAYMENT_PROCESSING,
                exception.getErrorCode()
        );

        assertEquals(PaymentStatus.PAID, payment.getPaymentStatus());
        assertNull(payment.getCanceledAt());

        verify(paymentOperationService)
                .existsOperation(CANCEL_IDEMPOTENCY_KEY);

        verifyNoMoreInteractions(paymentOperationService);

        verify(paymentRepository, never()).save(any(Payment.class));

        verifyNoInteractions(
                couponService, redisExpirationKeyService
        );
    }

    @Test
    @DisplayName("Toss 결제 승인 요청 전에 결제 처리 이력을 생성한다")
    void confirmPayment_operationExecutionOrder() {

        Order order = createPendingOrder();

        mockOrderLookup(order);
        mockConfirmOperation();
        mockPaymentSave();

        expectConfirmRequest(request -> {

            verify(paymentOperationService).createOperation(
                    ORDER_ID,
                    TOSS_ORDER_ID,
                    PAYMENT_KEY,
                    AMOUNT,
                    CONFIRM_IDEMPOTENCY_KEY,
                    PaymentOperationType.CONFIRM
            );

            verify(paymentOperationService, never())
                    .completeOperation(anyLong());

            verify(paymentRepository, never()).save(any(Payment.class));

            return withSuccess().createResponse(request);
        });

        paymentService.confirmPayment(
                USERNAME, createRequest(AMOUNT)
        );

        InOrder inOrder = inOrder(
                paymentOperationService, paymentRepository
        );

        inOrder.verify(paymentOperationService)
                .existsOperation(CONFIRM_IDEMPOTENCY_KEY);

        inOrder.verify(paymentOperationService).createOperation(
                ORDER_ID,
                TOSS_ORDER_ID,
                PAYMENT_KEY,
                AMOUNT,
                CONFIRM_IDEMPOTENCY_KEY,
                PaymentOperationType.CONFIRM
        );

        inOrder.verify(paymentRepository).save(any(Payment.class));

        inOrder.verify(paymentOperationService)
                .completeOperation(OPERATION_ID);
    }

    @Test
    @DisplayName("DB 커밋 콜백 이전에는 Redis 키를 삭제하지 않는다")
    void confirmPayment_redisAfterCommit() {

        mockOrderLookup(createPendingOrder());

        mockConfirmOperation();
        mockPaymentSave();

        expectConfirmRequest(withSuccess());

        paymentService.confirmPayment(
                USERNAME, createRequest(AMOUNT)
        );

        verifyNoInteractions(redisExpirationKeyService);

        assertEquals(
                1,
                TransactionSynchronizationManager.getSynchronizations()
                        .size()
        );

        executeAfterCommit();

        verify(redisExpirationKeyService).delete(REDIS_KEY);
    }

    @Test
    @DisplayName("커밋 후 Redis 키 삭제가 실패해도 예외를 전파하지 않는다")
    void confirmPayment_redisDeleteFailed() {

        Order order = createPendingOrder();

        mockOrderLookup(order);
        mockConfirmOperation();
        mockPaymentSave();

        expectConfirmRequest(withSuccess());

        doThrow(new IllegalStateException("Redis unavailable"))
                .when(redisExpirationKeyService)
                .delete(REDIS_KEY);

        PaymentResponse response = paymentService.confirmPayment(
                USERNAME, createRequest(AMOUNT)
        );

        assertDoesNotThrow(this::executeAfterCommit);

        verify(redisExpirationKeyService).delete(REDIS_KEY);

        assertEquals(PaymentStatus.PAID, response.paymentStatus());
        assertEquals(OrderStatus.PAID, order.getOrderStatus());
    }

    @Test
    @DisplayName("Toss 결제 상태 조회에 성공한다")
    void getTossPayment_success() {

        expectLookupSuccess("DONE", TOSS_ORDER_ID, AMOUNT);

        TossPaymentResponse response =
                paymentService.getTossPayment(PAYMENT_KEY);

        assertEquals(PAYMENT_KEY, response.paymentKey());
        assertEquals(TOSS_ORDER_ID, response.orderId());
        assertEquals(AMOUNT, response.totalAmount());
        assertEquals(AMOUNT, response.balanceAmount());

        assertTrue(response.isDone());
        assertFalse(response.isCanceled());
    }

    @Test
    @DisplayName("Toss 조회 응답의 결제 키가 다르면 예외가 발생한다")
    void getTossPayment_invalidResponse() {

        expectLookupRequest(withSuccess("""
                {
                    "paymentKey": "other-key",
                    "orderId": "test-toss-order-id",
                    "status": "DONE",
                    "totalAmount": 30000,
                    "balanceAmount": 30000
                }
                """, MediaType.APPLICATION_JSON));

        PaymentException exception = assertThrows(
                PaymentException.class,
                () -> paymentService.getTossPayment(PAYMENT_KEY)
        );

        assertEquals(
                PaymentErrorCode.INVALID_TOSS_PAYMENT_RESPONSE,
                exception.getErrorCode()
        );
    }

    @ParameterizedTest
    @EnumSource(TossFailureType.class)
    @DisplayName("Toss 결제 조회 실패 시 적절한 예외가 발생한다")
    void getTossPayment_apiFailed(TossFailureType failureType) {

        expectLookupRequest(createFailureResponse(failureType));

        PaymentErrorCode expectedErrorCode = switch (failureType) {

            case CLIENT_ERROR ->
                    PaymentErrorCode.TOSS_PAYMENT_LOOKUP_FAILED;

            case SERVER_ERROR ->
                    PaymentErrorCode.TOSS_SERVER_ERROR;

            case CONNECTION_ERROR ->
                    PaymentErrorCode.PAYMENT_CONNECTION_ERROR;
        };

        PaymentException exception = assertThrows(
                PaymentException.class,
                () -> paymentService.getTossPayment(PAYMENT_KEY)
        );

        assertEquals(expectedErrorCode, exception.getErrorCode());
    }

    @Test
    @DisplayName("Toss 결제 완료 후 내부 결제가 누락되면 복구한다")
    void recoverConfirmPayment_success() {

        Order order = createOrder(
                OrderStatus.PENDING,
                LocalDateTime.now().minusMinutes(5),
                null
        );

        PaymentOperation operation = createConfirmOperation();

        mockRecoveryLookup(order, operation);

        when(paymentRepository.findByOrder_OrderId(ORDER_ID))
                .thenReturn(Optional.empty());

        mockPaymentSave();

        expectLookupSuccess("DONE", TOSS_ORDER_ID, AMOUNT);

        paymentService.recoverConfirmPayment(
                ORDER_ID, OPERATION_ID
        );

        ArgumentCaptor<Payment> paymentCaptor =
                ArgumentCaptor.forClass(Payment.class);

        verify(paymentRepository).save(paymentCaptor.capture());

        Payment savedPayment = paymentCaptor.getValue();

        assertSame(order, savedPayment.getOrder());
        assertEquals(PAYMENT_KEY, savedPayment.getPaymentKey());
        assertEquals(AMOUNT, savedPayment.getAmount());
        assertEquals(PaymentStatus.PAID, savedPayment.getPaymentStatus());

        assertEquals(OrderStatus.PAID, order.getOrderStatus());
        assertNotNull(order.getPaymentAt());

        assertEquals(
                PaymentOperationStatus.COMPLETED,
                operation.getOperationStatus()
        );

        verifyNoInteractions(redisExpirationKeyService);

        executeAfterCommit();

        verify(redisExpirationKeyService).delete(REDIS_KEY);
    }

    @ParameterizedTest
    @EnumSource(
            value = PaymentOperationStatus.class,
            names = {"COMPLETED", "FAILED"}
    )
    @DisplayName("완료되거나 실패가 확정된 결제 처리 이력은 복구하지 않는다")
    void recoverConfirmPayment_skipOperation(
            PaymentOperationStatus status
    ) {

        Order order = createPendingOrder();

        PaymentOperation operation = createConfirmOperation();

        switch (status) {

            case COMPLETED -> operation.complete();

            case FAILED -> operation.fail();

            default -> throw new IllegalArgumentException();
        }

        mockRecoveryLookup(order, operation);

        paymentService.recoverConfirmPayment(
                ORDER_ID, OPERATION_ID
        );

        assertEquals(status, operation.getOperationStatus());

        assertEquals(OrderStatus.PENDING, order.getOrderStatus());

        verifyConfirmNotExecuted();

        assertTrue(
                TransactionSynchronizationManager.getSynchronizations()
                        .isEmpty()
        );
    }

    @Test
    @DisplayName("이미 내부 결제가 완료되었다면 처리 이력만 완료한다")
    void recoverConfirmPayment_alreadyPaid() {

        Order order = createPaidOrder();

        PaymentOperation operation = createConfirmOperation();

        Payment payment = createPayment(order, PaymentStatus.PAID);

        mockRecoveryLookup(order, operation);
        mockPaymentLookup(payment);

        expectLookupSuccess("DONE", TOSS_ORDER_ID, AMOUNT);

        paymentService.recoverConfirmPayment(
                ORDER_ID, OPERATION_ID
        );

        assertEquals(
                PaymentOperationStatus.COMPLETED,
                operation.getOperationStatus()
        );

        assertEquals(OrderStatus.PAID, order.getOrderStatus());

        assertEquals(PaymentStatus.PAID, payment.getPaymentStatus());

        verify(paymentRepository, never()).save(any(Payment.class));

        verifyNoInteractions(
                couponService, redisExpirationKeyService
        );

        executeAfterCommit();

        verify(redisExpirationKeyService).delete(REDIS_KEY);
    }

    @Test
    @DisplayName("Toss 주문 ID가 일치하지 않으면 결제를 복구하지 않는다")
    void recoverConfirmPayment_mismatch() {

        Order order = createPendingOrder();

        PaymentOperation operation = createConfirmOperation();

        mockRecoveryLookup(order, operation);

        expectLookupSuccess("DONE", "other-order-id", AMOUNT);

        PaymentException exception = assertThrows(
                PaymentException.class,
                () -> paymentService.recoverConfirmPayment(
                        ORDER_ID, OPERATION_ID
                )
        );

        assertEquals(
                PaymentErrorCode.PAYMENT_OPERATION_MISMATCH,
                exception.getErrorCode()
        );

        assertEquals(
                PaymentOperationStatus.PENDING,
                operation.getOperationStatus()
        );

        assertEquals(OrderStatus.PENDING, order.getOrderStatus());

        verifyConfirmNotExecuted();
    }

    private enum FinalTossStatus {

        ABORTED,
        EXPIRED,
        CANCELED
    }

    @ParameterizedTest
    @EnumSource(FinalTossStatus.class)
    @DisplayName("외부 결제 실패 또는 전액 취소가 확정되면 처리 이력을 FAILED로 변경한다")
    void recoverConfirmPayment_finalFailure(FinalTossStatus status) {

        Order order = createPendingOrder();

        PaymentOperation operation = createConfirmOperation();

        mockRecoveryLookup(order, operation);

        when(paymentRepository.findByOrder_OrderId(ORDER_ID))
                .thenReturn(Optional.empty());

        expectLookupSuccess(status.name(), TOSS_ORDER_ID, 0);

        paymentService.recoverConfirmPayment(
                ORDER_ID, OPERATION_ID
        );

        assertEquals(
                PaymentOperationStatus.FAILED,
                operation.getOperationStatus()
        );

        assertEquals(OrderStatus.PENDING, order.getOrderStatus());

        verify(paymentRepository, never()).save(any(Payment.class));

        verifyNoInteractions(
                couponService, redisExpirationKeyService
        );
    }

    @Test
    @DisplayName("외부 결제 결과가 미확정이면 PENDING 상태를 유지한다")
    void recoverConfirmPayment_unresolved() {

        Order order = createPendingOrder();

        PaymentOperation operation = createConfirmOperation();

        mockRecoveryLookup(order, operation);

        expectLookupSuccess(
                "IN_PROGRESS", TOSS_ORDER_ID, AMOUNT
        );

        paymentService.recoverConfirmPayment(
                ORDER_ID, OPERATION_ID
        );

        assertEquals(
                PaymentOperationStatus.PENDING,
                operation.getOperationStatus()
        );

        assertEquals(OrderStatus.PENDING, order.getOrderStatus());

        verifyConfirmNotExecuted();
    }

    @Test
    @DisplayName("복구 중 Toss 조회가 실패하면 결제 상태를 변경하지 않는다")
    void recoverConfirmPayment_lookupFailed() {

        Order order = createPendingOrder();

        PaymentOperation operation = createConfirmOperation();

        mockRecoveryLookup(order, operation);

        expectLookupRequest(
                withException(new IOException("Connection failed"))
        );

        PaymentException exception = assertThrows(
                PaymentException.class,
                () -> paymentService.recoverConfirmPayment(
                        ORDER_ID, OPERATION_ID
                )
        );

        assertEquals(
                PaymentErrorCode.PAYMENT_CONNECTION_ERROR,
                exception.getErrorCode()
        );

        assertEquals(
                PaymentOperationStatus.PENDING,
                operation.getOperationStatus()
        );

        assertEquals(OrderStatus.PENDING, order.getOrderStatus());

        verifyConfirmNotExecuted();
    }
}
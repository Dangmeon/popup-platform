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
import com.popq.platform.payment.entity.Payment;
import com.popq.platform.payment.entity.PaymentStatus;
import com.popq.platform.payment.exception.PaymentErrorCode;
import com.popq.platform.payment.exception.PaymentException;
import com.popq.platform.payment.repository.PaymentRepository;

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

    private PaymentService paymentService;
    private MockRestServiceServer mockServer;

    // 공통 테스트 데이터

    private static final String USERNAME = "testUser";
    private static final Long ORDER_ID = 1L;
    private static final Long PAYMENT_ID = 2L;
    private static final Long COUPON_ID = 3L;

    private static final String TOSS_ORDER_ID = "test-toss-order-id";
    private static final String PAYMENT_KEY = "test-payment-key";
    private static final String SECRET_KEY = "test-secret-key";

    private static final int AMOUNT = 30000;
    private static final String REDIS_KEY = "order:pending:" + ORDER_ID;

    private static final String CONFIRM_URL = "https://api.tosspayments.com/v1/payments/confirm";
    private static final String CANCEL_URL = "https://api.tosspayments.com/v1/payments/" + PAYMENT_KEY + "/cancel";

    private static final String BASIC_AUTH = "Basic " + Base64.getEncoder()
            .encodeToString((SECRET_KEY + ":").getBytes(StandardCharsets.UTF_8));

    // 테스트 초기 설정
    @BeforeEach
    void setUp() {
        // 실제 RestClient Builder 생성
        RestClient.Builder builder = RestClient.builder();

        // 실제 외부 HTTP 통신 대신 Mock 서버 연결
        mockServer = MockRestServiceServer.bindTo(builder).build();

        RestClient restClient = builder.build();

        // 실제 PaymentService 생성
        paymentService = new PaymentService(
                paymentRepository, orderRepository, redisExpirationKeyService,
                restClient, couponService
        );

        // 실제 Secret Key 대신 테스트용 값 설정
        ReflectionTestUtils.setField(paymentService, "tossSecretKey", SECRET_KEY);
    }

    @AfterEach
    void tearDown() {
        // 등록한 HTTP 요청이 모두 실행되었는지 검증
        mockServer.verify();
    }

    // 공통 테스트 데이터 생성 메서드

    private User createUser() {
        return User.builder()
                .username(USERNAME)
                .password("testPassword")
                .name("테스트 회원")
                .phone("01012345678")
                .build();
    }

    // 실제 결제 요청 DTO 생성
    private PaymentRequest createRequest(int amount) {
        PaymentRequest request = new PaymentRequest();

        ReflectionTestUtils.setField(request, "orderId", TOSS_ORDER_ID);
        ReflectionTestUtils.setField(request, "paymentKey", PAYMENT_KEY);
        ReflectionTestUtils.setField(request, "amount", amount);

        return request;
    }

    // 실제 주문 엔티티 생성
    private Order createOrder(OrderStatus status, LocalDateTime expiresAt, UserCoupon userCoupon) {
        Order order = Order.createOrder(
                createUser(), AMOUNT, expiresAt,
                "테스트 회원", "01012345678", "테스트 주소",
                "101호", "문 앞에 놓아주세요", userCoupon
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

    // 만료되지 않은 PENDING 주문 생성
    private Order createPendingOrder() {
        return createOrder(OrderStatus.PENDING, LocalDateTime.now().plusMinutes(10), null);
    }

    // 결제 완료 주문 생성
    private Order createPaidOrder() {
        return createOrder(OrderStatus.PAID, LocalDateTime.now().plusMinutes(10), null);
    }

    // 실제 Payment 엔티티 생성
    private Payment createPayment(Order order, PaymentStatus status) {
        Payment payment = Payment.create(order, PAYMENT_KEY, AMOUNT);

        ReflectionTestUtils.setField(payment, "paymentId", PAYMENT_ID);

        if (status == PaymentStatus.CANCELLED) {
            payment.cancel();
        }

        return payment;
    }

    // 주문 비관적 락 조회 설정
    private void mockOrderLookup(Order order) {
        when(orderRepository.findByTossOrderIdForUpdate(TOSS_ORDER_ID))
                .thenReturn(Optional.of(order));
    }

    // 결제 정보 조회 설정
    private void mockPaymentLookup(Payment payment) {
        when(paymentRepository.findByOrder_OrderId(ORDER_ID))
                .thenReturn(Optional.of(payment));
    }

    // DB 결제 ID 할당 동작 모방
    private void mockPaymentSave() {
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> {
                    Payment payment = invocation.getArgument(0);
                    ReflectionTestUtils.setField(payment, "paymentId", PAYMENT_ID);
                    return payment;
                });
    }

    // 결제 승인 HTTP 요청 및 응답 설정
    private void expectConfirmRequest(ResponseCreator responseCreator) {
        mockServer.expect(requestTo(CONFIRM_URL))
                // HTTP Method 검증
                .andExpect(method(HttpMethod.POST))

                // Basic Auth 검증
                .andExpect(header(HttpHeaders.AUTHORIZATION, BASIC_AUTH))

                // Content-Type 검증
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))

                // HTTP 요청 Body 검증
                .andExpect(jsonPath("$.paymentKey").value(PAYMENT_KEY))
                .andExpect(jsonPath("$.orderId").value(TOSS_ORDER_ID))
                .andExpect(jsonPath("$.amount").value(AMOUNT))
                .andRespond(responseCreator);
    }

    // 결제 취소 HTTP 요청 및 응답 설정
    private void expectCancelRequest(ResponseCreator responseCreator) {
        mockServer.expect(requestTo(CANCEL_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, BASIC_AUTH))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))

                // 취소 사유 검증
                .andExpect(jsonPath("$.cancelReason").value("고객 요청"))
                .andRespond(responseCreator);
    }

    // 결제 승인 실패 이후 후속 처리 방지 검증
    private void verifyConfirmNotExecuted() {
        verifyNoInteractions(paymentRepository, couponService, redisExpirationKeyService);
    }

    // Toss API 오류 유형
    private enum TossFailureType {

        CLIENT_ERROR,
        SERVER_ERROR,
        CONNECTION_ERROR
    }

    // Toss API 오류 응답 생성
    private ResponseCreator createFailureResponse(TossFailureType failureType) {

        return switch (failureType) {

            case CLIENT_ERROR -> withStatus(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {
                                "code": "TEST_CLIENT_ERROR",
                                "message": "요청 오류"
                            }
                            """);
            case SERVER_ERROR -> withStatus(HttpStatus.INTERNAL_SERVER_ERROR);
            case CONNECTION_ERROR -> withException(new IOException("Connection failed"));
        };
    }

    // 결제 승인 오류에 대한 기대 에러 코드
    private PaymentErrorCode getConfirmErrorCode(TossFailureType failureType) {

        return switch (failureType) {
            case CLIENT_ERROR -> PaymentErrorCode.TOSS_PAYMENT_FAILED;
            case SERVER_ERROR -> PaymentErrorCode.TOSS_SERVER_ERROR;
            case CONNECTION_ERROR -> PaymentErrorCode.PAYMENT_CONNECTION_ERROR;
        };
    }

    // 결제 취소 오류에 대한 기대 에러 코드
    private PaymentErrorCode getCancelErrorCode(TossFailureType failureType) {

        return switch (failureType) {
            case CLIENT_ERROR -> PaymentErrorCode.TOSS_CANCEL_FAILED;
            case SERVER_ERROR -> PaymentErrorCode.TOSS_SERVER_ERROR;
            case CONNECTION_ERROR -> PaymentErrorCode.PAYMENT_CONNECTION_ERROR;
        };
    }

    @Test
    @DisplayName("존재하지 않는 주문은 결제할 수 없다")
    void confirmPayment_orderNotFound() {

        PaymentRequest request = createRequest(AMOUNT);

        when(orderRepository.findByTossOrderIdForUpdate(TOSS_ORDER_ID)).thenReturn(Optional.empty());

        OrderException exception = assertThrows(OrderException.class, () -> paymentService.confirmPayment(request));

        assertEquals(OrderErrorCode.ORDER_NOT_FOUND, exception.getErrorCode());
        verify(orderRepository).findByTossOrderIdForUpdate(TOSS_ORDER_ID);

        // 결제 저장, 쿠폰 사용 및 Redis 삭제 방지
        verifyConfirmNotExecuted();

        // HTTP 요청을 등록하지 않았으므로
        // Toss API가 호출되면 테스트 실패
    }


    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PAID", "CANCELLED", "EXPIRED"})
    @DisplayName("PENDING 상태가 아닌 주문은 결제할 수 없다")
    void confirmPayment_invalidOrderStatus(OrderStatus status) {

        Order order = createOrder(status, LocalDateTime.now().plusMinutes(10), null);
        PaymentRequest request = createRequest(AMOUNT);

        mockOrderLookup(order);

        OrderException exception = assertThrows(
                OrderException.class,
                () -> paymentService.confirmPayment(request));

        assertEquals(OrderErrorCode.INVALID_ORDER_STATUS, exception.getErrorCode());

        // 기존 주문 상태 유지
        assertEquals(status, order.getOrderStatus());

        verifyConfirmNotExecuted();
    }

    @Test
    @DisplayName("만료 시간이 지난 PENDING 주문은 결제할 수 없다")
    void confirmPayment_orderExpired() {

        Order order = createOrder(OrderStatus.PENDING, LocalDateTime.now().minusMinutes(5), null);
        PaymentRequest request = createRequest(AMOUNT);

        mockOrderLookup(order);

        OrderException exception = assertThrows(
                OrderException.class,
                () -> paymentService.confirmPayment(request));

        assertEquals(OrderErrorCode.ORDER_EXPIRED, exception.getErrorCode());

        // 현재 결제 서비스에서는 만료 처리를
        // 직접 수행하지 않으므로 PENDING 유지
        assertEquals(OrderStatus.PENDING, order.getOrderStatus());
        assertNull(order.getPaymentAt());

        verifyConfirmNotExecuted();
    }

    @Test
    @DisplayName("주문 금액과 결제 요청 금액이 다르면 승인할 수 없다")
    void confirmPayment_amountMismatch() {

        Order order = createPendingOrder();

        // 실제 주문 금액: 30,000원
        // 결제 요청 금액: 20,000원
        PaymentRequest request = createRequest(20000);

        mockOrderLookup(order);

        PaymentException exception = assertThrows(
                PaymentException.class,
                () -> paymentService.confirmPayment(request));

        assertEquals(PaymentErrorCode.AMOUNT_MISMATCH, exception.getErrorCode());
        assertEquals(OrderStatus.PENDING, order.getOrderStatus());
        assertNull(order.getPaymentAt());

        verifyConfirmNotExecuted();
    }

    @Test
    @DisplayName("결제 승인 성공 시 결제 정보 저장 및 주문 상태 변경이 수행된다")
    void confirmPayment_success() {

        Order order = createPendingOrder();
        PaymentRequest request = createRequest(AMOUNT);

        mockOrderLookup(order);
        mockPaymentSave();

        // Toss 결제 승인 성공 응답 설정
        expectConfirmRequest(withSuccess());

        LocalDateTime before = LocalDateTime.now();

        PaymentResponse response = paymentService.confirmPayment(request);

        LocalDateTime after = LocalDateTime.now();

        // 결제 응답 검증
        assertEquals(PAYMENT_ID, response.paymentId());
        assertEquals(ORDER_ID, response.orderId());
        assertEquals(PAYMENT_KEY, response.paymentKey());
        assertEquals(AMOUNT, response.amount());
        assertEquals(PaymentStatus.PAID, response.paymentStatus());
        assertNotNull(response.paidAt());
        assertNull(response.canceledAt());

        // 결제 시간 검증
        assertFalse(response.paidAt().isBefore(before));
        assertFalse(response.paidAt().isAfter(after));

        // 실제 저장된 Payment 검증
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);

        InOrder inOrder = inOrder(orderRepository, paymentRepository, redisExpirationKeyService);

        inOrder.verify(orderRepository).findByTossOrderIdForUpdate(TOSS_ORDER_ID);
        inOrder.verify(paymentRepository).save(paymentCaptor.capture());
        inOrder.verify(redisExpirationKeyService).delete(REDIS_KEY);

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

        // 결제 정보와 주문 금액 일치 검증
        assertEquals(order.getTotalPrice(), savedPayment.getAmount());

        // 쿠폰 없는 주문이므로 쿠폰 서비스 미실행
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

        Order order = createOrder(OrderStatus.PENDING, LocalDateTime.now().plusMinutes(10), userCoupon);
        PaymentRequest request = createRequest(AMOUNT);

        mockOrderLookup(order);
        mockPaymentSave();

        expectConfirmRequest(withSuccess());

        PaymentResponse response = paymentService.confirmPayment(request);

        assertEquals(PaymentStatus.PAID, response.paymentStatus());
        assertEquals(OrderStatus.PAID, order.getOrderStatus());
        assertEquals(AMOUNT, response.amount());

        // 결제 저장 → 쿠폰 사용 → Redis 키 삭제
        InOrder inOrder = inOrder(paymentRepository, couponService, redisExpirationKeyService);

        inOrder.verify(paymentRepository).save(any(Payment.class));
        inOrder.verify(couponService).useCoupon(USERNAME, COUPON_ID);
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
        expectConfirmRequest(createFailureResponse(failureType));

        PaymentErrorCode expectedErrorCode = getConfirmErrorCode(failureType);

        PaymentException exception = assertThrows(
                PaymentException.class,
                () -> paymentService.confirmPayment(request));

        assertEquals(expectedErrorCode, exception.getErrorCode());

        // 결제 승인 실패 시 주문 상태 유지
        assertEquals(OrderStatus.PENDING, order.getOrderStatus());
        assertNull(order.getPaymentAt());

        // Payment 저장, 쿠폰 사용, Redis 삭제 방지
        verifyConfirmNotExecuted();
    }

    @Test
    @DisplayName("존재하지 않는 결제 정보는 취소할 수 없다")
    void cancelPayment_paymentNotFound() {

        Order order = createPaidOrder();

        when(paymentRepository.findByOrder_OrderId(ORDER_ID)).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(
                PaymentException.class,
                () -> paymentService.cancelPayment(order)
        );

        assertEquals(PaymentErrorCode.PAYMENT_NOT_FOUND, exception.getErrorCode());
        verify(paymentRepository).findByOrder_OrderId(ORDER_ID);
        assertEquals(OrderStatus.PAID, order.getOrderStatus());

        verifyNoMoreInteractions(paymentRepository);
        verifyNoInteractions(orderRepository, couponService, redisExpirationKeyService);
    }

    @Test
    @DisplayName("이미 취소된 결제는 중복 취소할 수 없다")
    void cancelPayment_alreadyCancelled() {

        Order order = createPaidOrder();
        Payment payment = createPayment(order, PaymentStatus.CANCELLED);
        LocalDateTime originalCanceledAt = payment.getCanceledAt();

        mockPaymentLookup(payment);

        PaymentException exception = assertThrows(
                PaymentException.class,
                () -> paymentService.cancelPayment(order));

        assertEquals(PaymentErrorCode.INVALID_PAYMENT_STATUS, exception.getErrorCode());
        assertEquals(PaymentStatus.CANCELLED, payment.getPaymentStatus());

        // 기존 취소 시간 유지
        assertEquals(originalCanceledAt, payment.getCanceledAt());

        verify(paymentRepository).findByOrder_OrderId(ORDER_ID);
        verifyNoMoreInteractions(paymentRepository);
        verifyNoInteractions(orderRepository, couponService, redisExpirationKeyService);

        // 외부 Toss 취소 요청이 실행되면 테스트 실패
    }

    @Test
    @DisplayName("정상 결제 취소 시 결제 상태와 취소 시간이 변경된다")
    void cancelPayment_success() {

        Order order = createPaidOrder();
        Payment payment = createPayment(order, PaymentStatus.PAID);
        LocalDateTime originalPaidAt = payment.getPaidAt();

        mockPaymentLookup(payment);

        // Toss 결제 취소 성공 응답 설정
        expectCancelRequest(withSuccess());

        LocalDateTime before = LocalDateTime.now();

        paymentService.cancelPayment(order);

        LocalDateTime after = LocalDateTime.now();

        // 결제 상태 변경 검증
        assertEquals(PaymentStatus.CANCELLED, payment.getPaymentStatus());

        // 결제 취소 시간 검증
        assertNotNull(payment.getCanceledAt());
        assertFalse(payment.getCanceledAt().isBefore(before));
        assertFalse(payment.getCanceledAt().isAfter(after));

        // 기존 결제 정보 유지 검증
        assertEquals(originalPaidAt, payment.getPaidAt());
        assertEquals(PAYMENT_KEY, payment.getPaymentKey());
        assertEquals(AMOUNT, payment.getAmount());

        // PaymentService는 Payment 상태만 변경
        // Order 상태 변경은 OrderService에서 수행
        assertEquals(OrderStatus.PAID, order.getOrderStatus());

        // 결제 조회 검증
        verify(paymentRepository).findByOrder_OrderId(ORDER_ID);

        // Dirty Checking 방식이므로 별도 저장 없음
        verify(paymentRepository, never()).save(any(Payment.class));

        verifyNoMoreInteractions(paymentRepository);
        verifyNoInteractions(orderRepository, couponService, redisExpirationKeyService);
    }

    @ParameterizedTest
    @EnumSource(TossFailureType.class)
    @DisplayName("Toss 결제 취소 실패 시 기존 결제 상태를 유지한다")
    void cancelPayment_tossApiFailed(TossFailureType failureType) {

        Order order = createPaidOrder();
        Payment payment = createPayment(order, PaymentStatus.PAID);
        LocalDateTime originalPaidAt = payment.getPaidAt();

        mockPaymentLookup(payment);
        expectCancelRequest(createFailureResponse(failureType));

        PaymentErrorCode expectedErrorCode = getCancelErrorCode(failureType);

        PaymentException exception = assertThrows(
                PaymentException.class,
                () -> paymentService.cancelPayment(order));

        assertEquals(expectedErrorCode, exception.getErrorCode());

        // 결제 취소 실패 시 PAID 유지
        assertEquals(PaymentStatus.PAID, payment.getPaymentStatus());
        assertNull(payment.getCanceledAt());
        assertEquals(originalPaidAt, payment.getPaidAt());
        assertEquals(OrderStatus.PAID, order.getOrderStatus());

        verify(paymentRepository).findByOrder_OrderId(ORDER_ID);
        verifyNoMoreInteractions(paymentRepository);
        verifyNoInteractions(orderRepository, couponService, redisExpirationKeyService);
    }
}

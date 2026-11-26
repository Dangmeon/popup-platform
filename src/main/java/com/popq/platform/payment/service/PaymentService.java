package com.popq.platform.payment.service;

import com.popq.platform.order.exception.OrderErrorCode;
import com.popq.platform.order.exception.OrderException;
import com.popq.platform.payment.dto.PaymentRequest;
import com.popq.platform.payment.dto.PaymentResponse;
import com.popq.platform.order.entity.Order;
import com.popq.platform.order.entity.OrderStatus;
import com.popq.platform.payment.entity.Payment;
import com.popq.platform.payment.entity.PaymentStatus;
import com.popq.platform.payment.exception.PaymentErrorCode;
import com.popq.platform.payment.exception.PaymentException;
import com.popq.platform.common.redis.RedisExpirationKeyService;
import com.popq.platform.order.repository.OrderRepository;
import com.popq.platform.payment.repository.PaymentRepository;
import com.popq.platform.coupon.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final RedisExpirationKeyService redisExpirationKeyService;
    private final RestClient restClient;
    private final CouponService couponService;

    @Value("${toss.secret-key}")
    private String tossSecretKey;

    @Transactional
    public PaymentResponse confirmPayment(String username, PaymentRequest request) {

        Order order = getOrderForUpdate(request.getOrderId());

        validateOrderOwner(order, username);

        if (order.getOrderStatus() == OrderStatus.PAID) {
            return getExistingPayment(order, request);
        }

        validatePayment(order, request);

        approveTossPayment(request);

        Payment payment = Payment.create(
                order,
                request.getPaymentKey(),
                request.getAmount()
        );

        paymentRepository.save(payment);

        if (order.getUserCoupon() != null) {
            couponService.useCoupon(
                    order.getUser().getUsername(),
                    order.getUserCoupon().getId()
            );
        }

        order.completePayment();

        redisExpirationKeyService.delete(
                "order:pending:" + order.getOrderId()
        );

        return PaymentResponse.from(payment);
    }

    @Transactional
    public void cancelPayment(Order order) {

        Payment payment = paymentRepository.findByOrder_OrderId(order.getOrderId())
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        validateCancelablePayment(payment);

        cancelTossPayment(payment.getPaymentKey());

        payment.cancel();
    }

    private void approveTossPayment(PaymentRequest request) {

        try {
            restClient.post()
                    .uri("https://api.tosspayments.com/v1/payments/confirm")
                    .headers(headers -> {
                        headers.setBasicAuth(tossSecretKey, "");
                        headers.set("Idempotency-Key", "confirm-" + request.getOrderId());
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();

        } catch (HttpClientErrorException e) {

            throw new PaymentException(PaymentErrorCode.TOSS_PAYMENT_FAILED);

        } catch (HttpServerErrorException e) {

            throw new PaymentException(PaymentErrorCode.TOSS_SERVER_ERROR);

        } catch (RestClientException e) {

            throw new PaymentException(PaymentErrorCode.PAYMENT_CONNECTION_ERROR);
        }
    }

    private void cancelTossPayment(String paymentKey) {

        Map<String, String> body = Map.of(
                "cancelReason", "고객 요청"
        );

        try {
            restClient.post()
                    .uri(
                            "https://api.tosspayments.com/v1/payments/{paymentKey}/cancel",
                            paymentKey
                    )
                    .headers(headers -> {
                        headers.setBasicAuth(tossSecretKey, "");
                        headers.set("Idempotency-Key", "cancel-" + paymentKey);
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

        } catch (HttpClientErrorException e) {

            throw new PaymentException(PaymentErrorCode.TOSS_CANCEL_FAILED);

        } catch (HttpServerErrorException e) {

            throw new PaymentException(PaymentErrorCode.TOSS_SERVER_ERROR);

        } catch (RestClientException e) {

            throw new PaymentException(PaymentErrorCode.PAYMENT_CONNECTION_ERROR);
        }
    }

    private Order getOrderForUpdate(String tossOrderId) {
        return orderRepository.findByTossOrderIdForUpdate(tossOrderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private void validatePayment(Order order, PaymentRequest request) {

        if (order.getOrderStatus() != OrderStatus.PENDING) {
            throw new OrderException(OrderErrorCode.INVALID_ORDER_STATUS);
        }

        if(!LocalDateTime.now().isBefore(order.getExpiresAt())){
            throw new OrderException(OrderErrorCode.ORDER_EXPIRED);
        }

        if (order.getTotalPrice() != request.getAmount()) {
            throw new PaymentException(PaymentErrorCode.AMOUNT_MISMATCH);
        }
    }

    private void validateCancelablePayment(Payment payment) {

        if (payment.getPaymentStatus() != PaymentStatus.PAID) {
            throw new PaymentException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }
    }

    private void validateOrderOwner(Order order, String username) {
        if (!order.getUser().getUsername().equals(username)) {
            throw new OrderException(OrderErrorCode.ORDER_ACCESS_DENIED);
        }
    }

    // 이미 결제 완료된 주문의 동일 결제 요청 처리
    private PaymentResponse getExistingPayment(Order order, PaymentRequest request) {

        Payment payment = paymentRepository.findByOrder_OrderId(order.getOrderId())
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        // 기존 결제 상태 검증
        if (payment.getPaymentStatus() != PaymentStatus.PAID) {
            throw new PaymentException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }

        // 기존 결제 키 검증
        if (!payment.getPaymentKey().equals(request.getPaymentKey())) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_KEY_MISMATCH);
        }

        // 기존 결제 금액 검증
        if (payment.getAmount() != request.getAmount() || order.getTotalPrice() != request.getAmount()) {
            throw new PaymentException(PaymentErrorCode.AMOUNT_MISMATCH);
        }

        return PaymentResponse.from(payment);
    }
}

package com.popq.platform.payment.service;

import com.popq.platform.order.exception.OrderErrorCode;
import com.popq.platform.order.exception.OrderException;
import com.popq.platform.payment.dto.PaymentRequest;
import com.popq.platform.payment.dto.PaymentResponse;
import com.popq.platform.order.entity.Order;
import com.popq.platform.order.entity.OrderStatus;
import com.popq.platform.payment.dto.TossPaymentResponse;
import com.popq.platform.payment.entity.*;
import com.popq.platform.payment.exception.PaymentErrorCode;
import com.popq.platform.payment.exception.PaymentException;
import com.popq.platform.common.redis.RedisExpirationKeyService;
import com.popq.platform.order.repository.OrderRepository;
import com.popq.platform.payment.repository.PaymentOperationRepository;
import com.popq.platform.payment.repository.PaymentRepository;
import com.popq.platform.coupon.service.CouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final RedisExpirationKeyService redisExpirationKeyService;
    private final RestClient restClient;
    private final CouponService couponService;
    private final PaymentOperationService paymentOperationService;
    private final PaymentOperationRepository paymentOperationRepository;

    @Value("${toss.secret-key}")
    private String tossSecretKey;

    @Transactional
    public PaymentResponse confirmPayment(String username, PaymentRequest request) {

        Order order = getOrderForUpdate(request.getOrderId());

        validateOrderOwner(order, username);

        // 이미 결제 완료된 주문의 멱등성 처리
        if (order.getOrderStatus() == OrderStatus.PAID) {
            return getExistingPayment(order, request);
        }

        validatePayment(order, request);

        String idempotencyKey = "confirm-" + order.getTossOrderId();

        // 기존 결제 처리 이력이 존재하면 중복 승인 방지
        if (paymentOperationService.existsOperation(idempotencyKey)) {
            throw new OrderException(OrderErrorCode.PAYMENT_PROCESSING);
        }

        // Toss API 호출 전 결제 처리 이력 독립 저장
        Long operationId = paymentOperationService.createOperation(
                order.getOrderId(), order.getTossOrderId(),
                request.getPaymentKey(), request.getAmount(),
                idempotencyKey, PaymentOperationType.CONFIRM
        );

        // 외부 결제 승인
        approveTossPayment(request);

        // 내부 결제 완료 처리
        Payment payment = completePayment(order, request.getPaymentKey(), request.getAmount());

        // 내부 결제 정보와 동일한 트랜잭션에서 완료 처리
        paymentOperationService.completeOperation(operationId);

        deleteExpirationKeyAfterCommit(order.getOrderId());

        return PaymentResponse.from(payment);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelPayment(Order order) {

        Payment payment = paymentRepository.findByOrder_OrderId(order.getOrderId())
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        validateCancelablePayment(payment);

        String idempotencyKey = "cancel-" + payment.getPaymentKey();

        if (paymentOperationService.existsOperation(idempotencyKey)) {
            throw new OrderException(OrderErrorCode.PAYMENT_PROCESSING);
        }

        Long operationId = paymentOperationService.createOperation(
                order.getOrderId(), order.getTossOrderId(), payment.getPaymentKey(),
                payment.getAmount(), idempotencyKey, PaymentOperationType.CANCEL
        );

        cancelTossPayment(payment.getPaymentKey());

        payment.cancel();

        paymentOperationService.completeOperation(operationId);
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

    // Toss 서버의 실제 결제 상태 조회
    public TossPaymentResponse getTossPayment(String paymentKey) {
        try {
            TossPaymentResponse response = restClient.get()
                    .uri("https://api.tosspayments.com/v1/payments/{paymentKey}", paymentKey)
                    .headers(headers -> headers.setBasicAuth(tossSecretKey, ""))
                    .retrieve()
                    .body(TossPaymentResponse.class);

            if (response == null || !paymentKey.equals(response.paymentKey())
                    || response.orderId() == null || response.status() == null
                    || response.totalAmount() == null || response.balanceAmount() == null) {
                throw new PaymentException(PaymentErrorCode.INVALID_TOSS_PAYMENT_RESPONSE);
            }

            return response;

        } catch (HttpClientErrorException e) {
            throw new PaymentException(PaymentErrorCode.TOSS_PAYMENT_LOOKUP_FAILED);

        } catch (HttpServerErrorException e) {
            throw new PaymentException(PaymentErrorCode.TOSS_SERVER_ERROR);

        } catch (RestClientException e) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_CONNECTION_ERROR);
        }
    }

    // 외부 결제 승인 이후 내부 DB 처리 실패 복구
    @Transactional
    public void recoverConfirmPayment(Long orderId, Long operationId) {

        // 기존 결제 승인 및 주문 만료와 동일한 순서로 락 획득
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        PaymentOperation operation = paymentOperationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_OPERATION_NOT_FOUND));

        if (!operation.getOrderId().equals(orderId)
                || operation.getOperationType() != PaymentOperationType.CONFIRM) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_OPERATION_MISMATCH);
        }

        // 이미 처리된 작업은 복구하지 않음
        if (operation.getOperationStatus() == PaymentOperationStatus.COMPLETED
                || operation.getOperationStatus() == PaymentOperationStatus.FAILED
                || operation.getOperationStatus() == PaymentOperationStatus.COMPENSATION_REQUIRED) {
            return;
        }

        // 실제 Toss 결제 상태 조회
        TossPaymentResponse tossPayment = getTossPayment(operation.getPaymentKey());

        // 외부 결제 정보와 내부 처리 이력 일치 여부 검증
        if (!operation.getTossOrderId().equals(tossPayment.orderId())
                || !order.getTossOrderId().equals(tossPayment.orderId())
                || operation.getAmount() != tossPayment.totalAmount()
                || order.getTotalPrice() != tossPayment.totalAmount()) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_OPERATION_MISMATCH);
        }

        // Toss에서는 결제가 완료된 상태
        if (tossPayment.isDone()) {

            if (tossPayment.balanceAmount() != operation.getAmount()) {
                throw new PaymentException(PaymentErrorCode.INVALID_TOSS_PAYMENT_RESPONSE);
            }

            if (order.getOrderStatus() == OrderStatus.PAID) {
                Payment payment = paymentRepository.findByOrder_OrderId(orderId)
                        .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND));

                if (payment.getPaymentStatus() != PaymentStatus.PAID
                        || !payment.getPaymentKey().equals(operation.getPaymentKey())
                        || payment.getAmount() != operation.getAmount()) {
                    throw new PaymentException(PaymentErrorCode.PAYMENT_OPERATION_MISMATCH);
                }

                operation.complete();
                deleteExpirationKeyAfterCommit(orderId);
                return;
            }

            // 재고가 유지된 PENDING 주문만 정상 결제 복구
            if (order.getOrderStatus() != OrderStatus.PENDING
                    || paymentRepository.findByOrder_OrderId(orderId).isPresent()) {
                throw new OrderException(OrderErrorCode.INVALID_ORDER_STATUS);
            }

            completePayment(order, operation.getPaymentKey(), operation.getAmount());

            operation.complete();

            deleteExpirationKeyAfterCommit(orderId);

            return;
        }

        // 외부 결제가 최종 실패하거나 전액 취소된 경우
        if ("ABORTED".equals(tossPayment.status())
                || "EXPIRED".equals(tossPayment.status())
                || (tossPayment.isCanceled() && tossPayment.balanceAmount() == 0)) {

            if (order.getOrderStatus() != OrderStatus.PENDING
                    || paymentRepository.findByOrder_OrderId(orderId).isPresent()) {
                throw new OrderException(OrderErrorCode.INVALID_ORDER_STATUS);
            }

            operation.fail("Toss 결제 상태: " + tossPayment.status());

            return;
        }

        // 아직 결제 결과가 확정되지 않은 경우
        operation.requireRecovery("Toss 결제 상태: " + tossPayment.status());
    }

    // 내부 정합성 복구 서비스에서 사용하는 보상 취소
    @Transactional(propagation = Propagation.MANDATORY)
    public void compensateTossPayment(String paymentKey) {
        cancelTossPayment(paymentKey);
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

    // 내부 결제 정보 저장 및 주문 상태 변경
    private Payment completePayment(Order order, String paymentKey, int amount) {

        Payment payment = Payment.create(order, paymentKey, amount);

        paymentRepository.save(payment);

        if (order.getUserCoupon() != null) {
            couponService.useCoupon(order.getUser().getUsername(), order.getUserCoupon().getId());
        }

        order.completePayment();

        return payment;
    }

    // DB 커밋 성공 이후 Redis 만료 키 삭제
    private void deleteExpirationKeyAfterCommit(Long orderId) {

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    redisExpirationKeyService.delete("order:pending:" + orderId);
                } catch (Exception e) {
                    log.error("결제 완료 후 Redis 만료 키 삭제 실패: orderId={}", orderId, e);
                }
            }
        });
    }
}

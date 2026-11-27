package com.popq.platform.payment.service;

import com.popq.platform.order.exception.OrderErrorCode;
import com.popq.platform.order.exception.OrderException;
import com.popq.platform.payment.dto.PaymentRequest;
import com.popq.platform.payment.dto.PaymentResponse;
import com.popq.platform.order.entity.Order;
import com.popq.platform.order.entity.OrderItem;
import com.popq.platform.order.entity.OrderStatus;
import com.popq.platform.payment.dto.TossPaymentResponse;
import com.popq.platform.payment.entity.*;
import com.popq.platform.payment.exception.PaymentErrorCode;
import com.popq.platform.payment.exception.PaymentException;
import com.popq.platform.common.redis.RedisExpirationKeyService;
import com.popq.platform.order.repository.OrderRepository;
import com.popq.platform.payment.repository.PaymentOperationRepository;
import com.popq.platform.payment.repository.PaymentRepository;
import com.popq.platform.product.repository.ProductRepository;
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
    private final ProductRepository productRepository;

    @Value("${toss.secret-key}")
    private String tossSecretKey;

    @Value("${toss.api-base-url:https://api.tosspayments.com}")
    private String tossApiBaseUrl;

    // 결제 승인
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

        // 내부 결제 완료
        Payment payment = completePayment(order, request.getPaymentKey(), request.getAmount());

        // 결제 처리 이력 완료
        paymentOperationService.completeOperation(operationId);

        // DB 커밋 이후 Redis 만료 키 삭제
        deleteExpirationKeyAfterCommit(order.getOrderId());

        return PaymentResponse.from(payment);
    }

    // 결제 취소
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelPayment(Order order) {

        Payment payment = findPayment(order.getOrderId());

        validateCancelablePayment(payment);

        String idempotencyKey = "cancel-" + payment.getPaymentKey();

        // 중복 취소 요청 방지
        if (paymentOperationService.existsOperation(idempotencyKey)) {
            throw new OrderException(OrderErrorCode.PAYMENT_PROCESSING);
        }

        // 결제 취소 처리 이력 독립 저장
        Long operationId = paymentOperationService.createOperation(
                order.getOrderId(), order.getTossOrderId(),
                payment.getPaymentKey(), payment.getAmount(),
                idempotencyKey, PaymentOperationType.CANCEL
        );

        // 외부 결제 취소
        cancelTossPayment(payment.getPaymentKey());

        // 내부 결제 취소
        payment.cancel();

        // 결제 처리 이력 완료
        paymentOperationService.completeOperation(operationId);
    }

    // Toss 결제 승인 요청
    private void approveTossPayment(PaymentRequest request) {

        try {
            restClient.post()
                    .uri(tossApiBaseUrl + "/v1/payments/confirm")
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

    // Toss 결제 취소 요청
    private void cancelTossPayment(String paymentKey) {

        Map<String, String> body = Map.of(
                "cancelReason", "고객 요청"
        );

        try {
            restClient.post()
                    .uri(
                            tossApiBaseUrl + "/v1/payments/{paymentKey}/cancel",
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
                    .uri(
                            tossApiBaseUrl + "/v1/payments/{paymentKey}",
                            paymentKey
                    )
                    .headers(headers -> headers.setBasicAuth(tossSecretKey, ""))
                    .retrieve()
                    .body(TossPaymentResponse.class);

            if (response == null
                    || !paymentKey.equals(response.paymentKey())
                    || response.orderId() == null
                    || response.status() == null
                    || response.totalAmount() == null
                    || response.balanceAmount() == null) {

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

    // 결제 승인 이후 DB 롤백 복구
    @Transactional
    public void recoverConfirmPayment(Long orderId, Long operationId) {

        Order order = findOrderForUpdate(orderId);

        PaymentOperation operation = findOperationForUpdate(
                orderId, operationId, PaymentOperationType.CONFIRM
        );

        // 미완료 작업만 복구
        if (operation.getOperationStatus() != PaymentOperationStatus.PENDING) {
            return;
        }

        // Toss 실제 결제 상태 조회
        TossPaymentResponse tossPayment = getTossPayment(operation.getPaymentKey());

        validateRecoveryPayment(order, operation, tossPayment);

        // Toss 결제 승인 완료
        if (tossPayment.isDone()) {

            if (tossPayment.balanceAmount() != operation.getAmount()) {
                throw new PaymentException(PaymentErrorCode.INVALID_TOSS_PAYMENT_RESPONSE);
            }

            // 이미 내부 결제가 완료된 경우
            if (order.getOrderStatus() == OrderStatus.PAID) {

                Payment payment = findPayment(orderId);

                validateRecoveryPaymentInfo(payment, operation);

                if (payment.getPaymentStatus() != PaymentStatus.PAID) {
                    throw new PaymentException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
                }

            } else {

                // 재고가 유지된 미결제 주문만 복구
                if (order.getOrderStatus() != OrderStatus.PENDING
                        || paymentRepository.findByOrder_OrderId(orderId).isPresent()) {

                    throw new OrderException(OrderErrorCode.INVALID_ORDER_STATUS);
                }

                // 내부 결제 정보 복구
                completePayment(
                        order, operation.getPaymentKey(), operation.getAmount()
                );
            }

            operation.complete();

            deleteExpirationKeyAfterCommit(orderId);

            return;
        }

        // 외부 결제 실패 또는 전액 취소 확정
        if ("ABORTED".equals(tossPayment.status())
                || "EXPIRED".equals(tossPayment.status())
                || (tossPayment.isCanceled() && tossPayment.balanceAmount() == 0)) {

            if (order.getOrderStatus() != OrderStatus.PENDING
                    || paymentRepository.findByOrder_OrderId(orderId).isPresent()) {

                throw new OrderException(OrderErrorCode.INVALID_ORDER_STATUS);
            }

            operation.fail();

            return;
        }

        // 결제 결과 미확정 시 PENDING 유지
        operation.touch();

        log.warn("결제 승인 복구 대기: operationId={}, status={}",
                operationId, tossPayment.status());
    }

    // 결제 취소 이후 DB 롤백 복구
    @Transactional
    public void recoverCancelPayment(Long orderId, Long operationId) {

        Order order = findOrderForUpdate(orderId);

        PaymentOperation operation = findOperationForUpdate(
                orderId, operationId, PaymentOperationType.CANCEL
        );

        // 미완료 작업만 복구
        if (operation.getOperationStatus() != PaymentOperationStatus.PENDING) {
            return;
        }

        Payment payment = findPayment(orderId);

        // Toss 실제 결제 상태 조회
        TossPaymentResponse tossPayment = getTossPayment(operation.getPaymentKey());

        validateRecoveryPayment(order, operation, tossPayment);

        validateRecoveryPaymentInfo(payment, operation);

        // 전액 취소가 확인되지 않으면 내부 상태 유지
        if (!tossPayment.isCanceled() || tossPayment.balanceAmount() != 0) {

            operation.touch();

            log.warn("결제 취소 복구 대기: operationId={}, status={}",
                    operationId, tossPayment.status());

            return;
        }

        // 이미 내부 취소가 완료된 경우
        if (order.getOrderStatus() == OrderStatus.CANCELLED
                && payment.getPaymentStatus() == PaymentStatus.CANCELLED) {

            operation.complete();

            return;
        }

        // 결제 완료 상태의 주문만 취소 복구
        if (order.getOrderStatus() != OrderStatus.PAID
                || payment.getPaymentStatus() != PaymentStatus.PAID) {

            throw new OrderException(OrderErrorCode.INVALID_ORDER_STATUS);
        }

        // 결제 취소
        payment.cancel();

        // 상품 재고 복구
        restoreStock(order);

        // 주문 취소
        order.cancel();

        // 결제 처리 이력 완료
        operation.complete();
    }

    // Toss 주문 ID로 주문 비관적 락 조회
    private Order getOrderForUpdate(String tossOrderId) {

        return orderRepository.findByTossOrderIdForUpdate(tossOrderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    // 내부 주문 ID로 주문 비관적 락 조회
    private Order findOrderForUpdate(Long orderId) {

        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    // 결제 정보 조회
    private Payment findPayment(Long orderId) {

        return paymentRepository.findByOrder_OrderId(orderId)
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    // 결제 처리 이력 비관적 락 조회
    private PaymentOperation findOperationForUpdate(
            Long orderId, Long operationId, PaymentOperationType type
    ) {

        PaymentOperation operation = paymentOperationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_OPERATION_NOT_FOUND));

        if (!operation.getOrderId().equals(orderId)
                || operation.getOperationType() != type) {

            throw new PaymentException(PaymentErrorCode.PAYMENT_OPERATION_MISMATCH);
        }

        return operation;
    }

    // 결제 승인 가능 여부 검증
    private void validatePayment(Order order, PaymentRequest request) {

        if (order.getOrderStatus() != OrderStatus.PENDING) {
            throw new OrderException(OrderErrorCode.INVALID_ORDER_STATUS);
        }

        if (!LocalDateTime.now().isBefore(order.getExpiresAt())) {
            throw new OrderException(OrderErrorCode.ORDER_EXPIRED);
        }

        if (order.getTotalPrice() != request.getAmount()) {
            throw new PaymentException(PaymentErrorCode.AMOUNT_MISMATCH);
        }
    }

    // 결제 취소 가능 여부 검증
    private void validateCancelablePayment(Payment payment) {

        if (payment.getPaymentStatus() != PaymentStatus.PAID) {
            throw new PaymentException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }
    }

    // 주문 소유권 검증
    private void validateOrderOwner(Order order, String username) {

        if (!order.getUser().getUsername().equals(username)) {
            throw new OrderException(OrderErrorCode.ORDER_ACCESS_DENIED);
        }
    }

    // 외부 결제 정보와 내부 주문 및 처리 이력 검증
    private void validateRecoveryPayment(
            Order order, PaymentOperation operation, TossPaymentResponse tossPayment
    ) {

        if (!operation.getPaymentKey().equals(tossPayment.paymentKey())
                || !operation.getTossOrderId().equals(tossPayment.orderId())
                || !order.getTossOrderId().equals(tossPayment.orderId())
                || operation.getAmount() != tossPayment.totalAmount()
                || order.getTotalPrice() != tossPayment.totalAmount()) {

            throw new PaymentException(PaymentErrorCode.PAYMENT_OPERATION_MISMATCH);
        }
    }

    // 기존 내부 결제 정보 검증
    private void validateRecoveryPaymentInfo(
            Payment payment, PaymentOperation operation
    ) {

        if (!payment.getPaymentKey().equals(operation.getPaymentKey())
                || payment.getAmount() != operation.getAmount()) {

            throw new PaymentException(PaymentErrorCode.PAYMENT_OPERATION_MISMATCH);
        }
    }

    // 이미 결제 완료된 주문의 동일 결제 요청 처리
    private PaymentResponse getExistingPayment(Order order, PaymentRequest request) {

        Payment payment = findPayment(order.getOrderId());

        validateCancelablePayment(payment);

        if (!payment.getPaymentKey().equals(request.getPaymentKey())) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_KEY_MISMATCH);
        }

        if (payment.getAmount() != request.getAmount()
                || order.getTotalPrice() != request.getAmount()) {

            throw new PaymentException(PaymentErrorCode.AMOUNT_MISMATCH);
        }

        return PaymentResponse.from(payment);
    }

    // 내부 결제 정보 저장 및 주문 상태 변경
    private Payment completePayment(Order order, String paymentKey, int amount) {

        Payment payment = Payment.create(order, paymentKey, amount);

        paymentRepository.save(payment);

        // 쿠폰을 사용한 경우 쿠폰 사용 처리
        if (order.getUserCoupon() != null) {

            couponService.useCoupon(
                    order.getUser().getUsername(),
                    order.getUserCoupon().getId()
            );
        }

        order.completePayment();

        return payment;
    }

    // 상품 재고 복구
    private void restoreStock(Order order) {

        for (OrderItem item : order.getOrderItems()) {

            int updatedRows = productRepository.increaseStock(
                    item.getProduct().getPrdId(),
                    item.getQuantity()
            );

            if (updatedRows == 0) {
                throw new OrderException(OrderErrorCode.STOCK_RESTORE_FAILED);
            }
        }
    }

    // DB 커밋 성공 이후 Redis 만료 키 삭제
    private void deleteExpirationKeyAfterCommit(Long orderId) {

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {

                        try {
                            redisExpirationKeyService.delete("order:pending:" + orderId);

                        } catch (Exception e) {
                            log.error("결제 완료 후 Redis 만료 키 삭제 실패: orderId={}", orderId, e);
                        }
                    }
                }
        );
    }
}
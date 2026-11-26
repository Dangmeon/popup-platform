package com.popq.platform.payment.service;

import com.popq.platform.order.entity.Order;
import com.popq.platform.order.entity.OrderStatus;
import com.popq.platform.order.exception.OrderErrorCode;
import com.popq.platform.order.exception.OrderException;
import com.popq.platform.order.repository.OrderRepository;
import com.popq.platform.payment.entity.PaymentOperation;
import com.popq.platform.payment.entity.PaymentOperationStatus;
import com.popq.platform.payment.entity.PaymentOperationType;
import com.popq.platform.payment.exception.PaymentErrorCode;
import com.popq.platform.payment.exception.PaymentException;
import com.popq.platform.payment.repository.PaymentOperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentOperationService {

    private final PaymentOperationRepository paymentOperationRepository;
    private final OrderRepository orderRepository;

    // 결제 작업 이력을 별도 트랜잭션으로 저장
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long createOperation(Long orderId, String tossOrderId, String paymentKey,
                                int amount, String idempotencyKey, PaymentOperationType operationType) {

        PaymentOperation existingOperation = paymentOperationRepository.findByIdempotencyKey(idempotencyKey)
                .orElse(null);

        if (existingOperation != null) {
            if (!existingOperation.getOrderId().equals(orderId)
                    || !existingOperation.getTossOrderId().equals(tossOrderId)
                    || !existingOperation.getPaymentKey().equals(paymentKey)
                    || existingOperation.getAmount() != amount
                    || existingOperation.getOperationType() != operationType) {

                throw new PaymentException(PaymentErrorCode.PAYMENT_OPERATION_MISMATCH);
            }

            return existingOperation.getOperationId();
        }

        PaymentOperation operation = PaymentOperation.create(
                orderId, tossOrderId, paymentKey, amount, idempotencyKey, operationType
        );

        return paymentOperationRepository.saveAndFlush(operation).getOperationId();
    }

    // 내부 DB 변경과 동일한 트랜잭션에서 작업 완료 처리
    @Transactional
    public void completeOperation(Long operationId) {

        PaymentOperation operation = getOperationForUpdate(operationId);

        if (operation.getOperationStatus() == PaymentOperationStatus.COMPLETED) {
            return;
        }

        operation.complete();
    }

    // 기존 비즈니스 트랜잭션이 롤백된 이후에도 복구 상태 저장
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requireRecovery(Long operationId, String failureReason) {
        PaymentOperation operation = getOperationForUpdate(operationId);

        if (operation.getOperationStatus() == PaymentOperationStatus.COMPLETED
                || operation.getOperationStatus() == PaymentOperationStatus.FAILED) {
            return;
        }

        operation.requireRecovery(failureReason);
    }

    // 외부 결제 실패가 확정된 경우
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failOperation(Long operationId, String failureReason) {
        PaymentOperation operation = getOperationForUpdate(operationId);

        if (operation.getOperationStatus() == PaymentOperationStatus.COMPLETED) {
            return;
        }

        operation.fail(failureReason);
    }

    // 결제 승인 결과가 아직 확정되지 않은 주문인지 확인
    @Transactional(readOnly = true)
    public boolean hasUnresolvedConfirm(Long orderId) {
        return paymentOperationRepository.existsByOrderIdAndOperationTypeAndOperationStatusIn(
                orderId, PaymentOperationType.CONFIRM,
                List.of(PaymentOperationStatus.PENDING, PaymentOperationStatus.RECOVERY_REQUIRED, PaymentOperationStatus.COMPENSATION_REQUIRED)
        );
    }

    @Transactional(readOnly = true)
    public boolean existsOperation(String idempotencyKey) {
        return paymentOperationRepository.findByIdempotencyKey(idempotencyKey).isPresent();
    }

    private PaymentOperation getOperationForUpdate(Long operationId) {
        return paymentOperationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_OPERATION_NOT_FOUND));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRecoveryFailure(Long operationId, String failureReason) {
        PaymentOperation operation = getOperationForUpdate(operationId);

        if (operation.getOperationStatus() == PaymentOperationStatus.COMPLETED
                || operation.getOperationStatus() == PaymentOperationStatus.FAILED) {
            return;
        }

        operation.increaseRetryCount();

        if (operation.getOperationStatus() != PaymentOperationStatus.COMPENSATION_REQUIRED) {
            operation.requireRecovery(failureReason);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requestCompensation(Long orderId, Long operationId) {

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        PaymentOperation operation = getOperationForUpdate(operationId);

        if (!operation.getOrderId().equals(orderId) || operation.getOperationType() != PaymentOperationType.CONFIRM) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_OPERATION_MISMATCH);
        }

        if (operation.getOperationStatus() != PaymentOperationStatus.RECOVERY_REQUIRED
                || order.getOrderStatus() == OrderStatus.PAID) {
            throw new OrderException(OrderErrorCode.INVALID_ORDER_STATUS);
        }

        operation.requireCompensation("내부 결제 복구 실패로 인한 보상 취소 요청");
    }
}
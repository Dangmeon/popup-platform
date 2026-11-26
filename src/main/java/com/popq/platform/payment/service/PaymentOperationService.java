package com.popq.platform.payment.service;

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

    // 결제 작업 이력을 별도 트랜잭션으로 저장
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long createOperation(
            Long orderId, String tossOrderId, String paymentKey,
            int amount, String idempotencyKey, PaymentOperationType operationType
    ) {

        PaymentOperation existingOperation = paymentOperationRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElse(null);

        // 동일한 결제 처리 이력이 존재하는 경우
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
                orderId, tossOrderId, paymentKey,
                amount, idempotencyKey, operationType
        );

        return paymentOperationRepository.saveAndFlush(operation).getOperationId();
    }

    // 내부 DB 변경과 동일한 트랜잭션에서 작업 완료
    @Transactional
    public void completeOperation(Long operationId) {

        PaymentOperation operation = getOperationForUpdate(operationId);

        if (operation.getOperationStatus() == PaymentOperationStatus.PENDING) {
            operation.complete();
        }
    }

    // 미완료 결제 승인 이력 존재 여부
    @Transactional(readOnly = true)
    public boolean hasUnresolvedConfirm(Long orderId) {

        return paymentOperationRepository.existsByOrderIdAndOperationTypeAndOperationStatus(
                orderId,
                PaymentOperationType.CONFIRM,
                PaymentOperationStatus.PENDING
        );
    }

    // 기존 결제 처리 이력 존재 여부
    @Transactional(readOnly = true)
    public boolean existsOperation(String idempotencyKey) {

        return paymentOperationRepository.findByIdempotencyKey(idempotencyKey).isPresent();
    }

    // 복구 실패 시 마지막 복구 시도 시간 갱신
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void touchOperation(Long operationId) {

        PaymentOperation operation = getOperationForUpdate(operationId);

        if (operation.getOperationStatus() == PaymentOperationStatus.PENDING) {
            operation.touch();
        }
    }

    // 결제 처리 이력 비관적 락 조회
    private PaymentOperation getOperationForUpdate(Long operationId) {

        return paymentOperationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_OPERATION_NOT_FOUND));
    }
}
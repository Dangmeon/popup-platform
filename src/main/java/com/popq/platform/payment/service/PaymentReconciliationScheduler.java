package com.popq.platform.payment.service;

import com.popq.platform.payment.entity.PaymentOperation;
import com.popq.platform.payment.entity.PaymentOperationStatus;
import com.popq.platform.payment.entity.PaymentOperationType;
import com.popq.platform.payment.repository.PaymentOperationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciliationScheduler {

    private static final long RECOVERY_INTERVAL = 60 * 1000L;
    private static final int RECOVERY_DELAY_MINUTES = 2;
    private static final int MAX_RETRY_COUNT = 3;
    private static final int BATCH_SIZE = 50;

    private final PaymentOperationRepository paymentOperationRepository;
    private final PaymentOperationService paymentOperationService;
    private final PaymentService paymentService;
    private final PaymentCancellationRecoveryService cancellationRecoveryService;
    private final PaymentCompensationService paymentCompensationService;

    @Scheduled(fixedDelay = RECOVERY_INTERVAL)
    public void reconcilePayments() {

        List<PaymentOperationStatus> statuses = List.of(
                PaymentOperationStatus.PENDING,
                PaymentOperationStatus.RECOVERY_REQUIRED,
                PaymentOperationStatus.COMPENSATION_REQUIRED
        );

        LocalDateTime before = LocalDateTime.now().minusMinutes(RECOVERY_DELAY_MINUTES);

        List<Long> operationIds = paymentOperationRepository.findRecoveryTargetIds(statuses, before, PageRequest.of(0, BATCH_SIZE));

        for (Long operationId : operationIds) {
            try {
                recoverOperation(operationId);
            } catch (Exception e) {
                log.error("결제 정합성 복구 실패: operationId={}", operationId, e);

                try {
                    paymentOperationService.recordRecoveryFailure(operationId, e.getClass().getSimpleName());
                } catch (Exception recordException) {
                    log.error("결제 복구 실패 이력 저장 실패: operationId={}", operationId, recordException);
                }
            }
        }
    }

    private void recoverOperation(Long operationId) {

        PaymentOperation operation = paymentOperationRepository.findById(operationId).orElse(null);

        if (operation == null) {
            return;
        }

        PaymentOperationStatus status = operation.getOperationStatus();

        if (status == PaymentOperationStatus.COMPLETED || status == PaymentOperationStatus.FAILED) {
            return;
        }

        if (operation.getRetryCount() >= MAX_RETRY_COUNT) {
            log.warn("결제 복구 반복 실패로 관리자 확인 필요: operationId={}, retryCount={}",
                    operationId, operation.getRetryCount());
        }

        if (status == PaymentOperationStatus.COMPENSATION_REQUIRED) {
            paymentCompensationService.compensatePayment(operation.getOrderId(), operationId);
            return;
        }

        if (operation.getOperationType() == PaymentOperationType.CONFIRM) {
            paymentService.recoverConfirmPayment(operation.getOrderId(), operationId);
            return;
        }

        if (operation.getOperationType() == PaymentOperationType.CANCEL) {
            cancellationRecoveryService.recoverCancelPayment(operation.getOrderId(), operationId);
        }
    }
}
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
    private static final int BATCH_SIZE = 50;

    private final PaymentOperationRepository paymentOperationRepository;
    private final PaymentOperationService paymentOperationService;
    private final PaymentService paymentService;

    // 미완료 결제 처리 이력 복구
    @Scheduled(fixedDelay = RECOVERY_INTERVAL)
    public void reconcilePayments() {

        LocalDateTime before = LocalDateTime.now()
                .minusMinutes(RECOVERY_DELAY_MINUTES);

        List<Long> operationIds = paymentOperationRepository.findRecoveryTargetIds(
                PaymentOperationStatus.PENDING,
                before,
                PageRequest.of(0, BATCH_SIZE)
        );

        for (Long operationId : operationIds) {

            try {
                recoverOperation(operationId);

            } catch (Exception e) {

                log.error("결제 복구 실패: operationId={}", operationId, e);

                try {
                    paymentOperationService.touchOperation(operationId);

                } catch (Exception touchException) {
                    log.error("복구 시도 시간 갱신 실패: operationId={}",
                            operationId, touchException);
                }
            }
        }
    }

    // 결제 작업 유형에 따른 복구 실행
    private void recoverOperation(Long operationId) {

        PaymentOperation operation = paymentOperationRepository
                .findById(operationId)
                .orElse(null);

        if (operation == null
                || operation.getOperationStatus() != PaymentOperationStatus.PENDING) {
            return;
        }

        if (operation.getOperationType() == PaymentOperationType.CONFIRM) {

            paymentService.recoverConfirmPayment(
                    operation.getOrderId(), operationId
            );

        } else if (operation.getOperationType() == PaymentOperationType.CANCEL) {

            paymentService.recoverCancelPayment(
                    operation.getOrderId(), operationId
            );
        }
    }
}
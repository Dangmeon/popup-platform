package com.popq.platform.payment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "payment_operation")
public class PaymentOperation {

    @Id
    @SequenceGenerator(
            name = "payment_operation_seq_gen",
            sequenceName = "PAYMENT_OPERATION_SEQ",
            allocationSize = 1
    )
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "payment_operation_seq_gen")
    @Column(name = "operation_id")
    private Long operationId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "toss_order_id", nullable = false)
    private String tossOrderId;

    @Column(name = "payment_key", nullable = false)
    private String paymentKey;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false)
    private PaymentOperationType operationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_status", nullable = false)
    private PaymentOperationStatus operationStatus;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public static PaymentOperation create(Long orderId, String tossOrderId, String paymentKey,
                                          int amount, String idempotencyKey, PaymentOperationType operationType) {

        PaymentOperation operation = new PaymentOperation();

        operation.orderId = orderId;
        operation.tossOrderId = tossOrderId;
        operation.paymentKey = paymentKey;
        operation.amount = amount;
        operation.idempotencyKey = idempotencyKey;
        operation.operationType = operationType;
        operation.operationStatus = PaymentOperationStatus.PENDING;
        operation.retryCount = 0;
        operation.createdAt = LocalDateTime.now();
        operation.updatedAt = LocalDateTime.now();

        return operation;
    }

    public void complete() {
        this.operationStatus = PaymentOperationStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.failureReason = null;
    }

    public void requireRecovery(String failureReason) {
        this.operationStatus = PaymentOperationStatus.RECOVERY_REQUIRED;
        this.failureReason = failureReason;
        this.updatedAt = LocalDateTime.now();
    }

    public void fail(String failureReason) {
        this.operationStatus = PaymentOperationStatus.FAILED;
        this.failureReason = failureReason;
        this.updatedAt = LocalDateTime.now();
    }

    public void increaseRetryCount() {
        this.retryCount++;
        this.updatedAt = LocalDateTime.now();
    }
}
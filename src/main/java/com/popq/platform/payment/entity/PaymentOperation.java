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

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 결제 처리 이력 생성
    public static PaymentOperation create(
            Long orderId, String tossOrderId, String paymentKey,
            int amount, String idempotencyKey, PaymentOperationType operationType
    ) {

        PaymentOperation operation = new PaymentOperation();

        operation.orderId = orderId;
        operation.tossOrderId = tossOrderId;
        operation.paymentKey = paymentKey;
        operation.amount = amount;
        operation.idempotencyKey = idempotencyKey;
        operation.operationType = operationType;
        operation.operationStatus = PaymentOperationStatus.PENDING;
        operation.createdAt = LocalDateTime.now();
        operation.updatedAt = operation.createdAt;

        return operation;
    }

    // 내부 결제 처리 완료
    public void complete() {
        this.operationStatus = PaymentOperationStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    // 외부 결제 실패 확정
    public void fail() {
        this.operationStatus = PaymentOperationStatus.FAILED;
        this.updatedAt = LocalDateTime.now();
    }

    // 복구 시도 시간 갱신
    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
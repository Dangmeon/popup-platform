package com.popq.platform.payment.entity;

public enum PaymentOperationStatus {
    PENDING,
    COMPLETED,
    RECOVERY_REQUIRED,
    COMPENSATION_REQUIRED,
    FAILED
}
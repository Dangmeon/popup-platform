package com.popq.platform.payment.dto;

import com.popq.platform.payment.entity.Payment;
import com.popq.platform.payment.entity.PaymentStatus;

import java.time.LocalDateTime;

public record PaymentResponse(
        Long paymentId,
        Long orderId,
        String paymentKey,
        int amount,
        PaymentStatus paymentStatus,
        LocalDateTime paidAt,
        LocalDateTime canceledAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getPaymentId(),
                payment.getOrder().getOrderId(),
                payment.getPaymentKey(),
                payment.getAmount(),
                payment.getPaymentStatus(),
                payment.getPaidAt(),
                payment.getCanceledAt()
        );
    }
}
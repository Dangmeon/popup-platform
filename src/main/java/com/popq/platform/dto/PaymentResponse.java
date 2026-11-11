package com.popq.platform.dto;

import com.popq.platform.entity.Payment;
import com.popq.platform.entity.PaymentStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class PaymentResponse {

    private Long paymentId;
    private Long orderId;
    private String paymentKey;
    private int amount;
    private PaymentStatus paymentStatus;
    private LocalDateTime paidAt;
    private LocalDateTime canceledAt;

    public static PaymentResponse from(Payment payment) {
        PaymentResponse response = new PaymentResponse();

        response.paymentId = payment.getPaymentId();
        response.orderId = payment.getOrder().getOrderId();
        response.paymentKey = payment.getPaymentKey();
        response.amount = payment.getAmount();
        response.paymentStatus = payment.getPaymentStatus();
        response.paidAt = payment.getPaidAt();
        response.canceledAt = payment.getCanceledAt();

        return response;
    }
}

package com.popq.platform.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// DTO에 정의하지 않은 응답 필드를 무시하기 위한 설정
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentResponse(
        String paymentKey,
        String orderId,
        String status,
        Integer totalAmount,
        Integer balanceAmount,
        String approvedAt
) {
    public boolean isDone() {
        return "DONE".equals(status);
    }

    public boolean isCanceled() {
        return "CANCELED".equals(status);
    }
}
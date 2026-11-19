package com.popq.platform.payment.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentRequest {

    private String paymentKey;
    private String orderId;
    private int amount;
}

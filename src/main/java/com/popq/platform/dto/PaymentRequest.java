package com.popq.platform.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentRequest {

    private String paymentKey;
    private String OrderId;
    private int amount;
}

package com.popq.platform.controller;

import com.popq.platform.dto.PaymentRequest;
import com.popq.platform.dto.PaymentResponse;
import com.popq.platform.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/confirm")
    public ResponseEntity<PaymentResponse> confirm(@RequestBody PaymentRequest request) {
        return ResponseEntity.ok(paymentService.confirmPayment(request));
    }
}

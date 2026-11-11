package com.popq.platform.service;

import com.popq.platform.entity.Order;
import com.popq.platform.entity.Payment;
import com.popq.platform.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public Payment savePayment(Order order, String paymentKey, int amount) {
        Payment payment = Payment.createPayment(
                order, paymentKey, amount
        );

        return paymentRepository.save(payment);
    }

    // TODO TossPayments 연동 후 결제 취소 구현
}

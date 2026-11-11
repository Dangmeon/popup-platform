package com.popq.platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@Getter
@Table(name = "payment")
public class Payment {

    @Id
    @SequenceGenerator(
            name = "payment_seq_gen",
            sequenceName = "PAYMENT_SEQ",
            allocationSize = 1
    )
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "payment_seq_gen"
    )
    @Column(name = "payment_id")
    private Long paymentId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(name = "payment_key", nullable = false, unique = true)
    private String paymentKey;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    public static Payment createPayment(Order order, String paymentKey, int amount){
        Payment payment = new Payment();

        payment.order = order;
        payment.paymentKey = paymentKey;
        payment.amount = amount;
        payment.paymentStatus = PaymentStatus.PAID;
        payment.paidAt = LocalDateTime.now();

        return payment;
    }

    public void cancel(){
        this.paymentStatus = PaymentStatus.CANCELLED;
        this.canceledAt = LocalDateTime.now();
    }
}

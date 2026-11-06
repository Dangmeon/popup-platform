package com.popq.platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "ORDERS")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ORDER_ID")
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MEMBER_ID", nullable = false)
    private User user;

    @Column(name = "TOTAL_PRICE", nullable = false)
    private Long totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "ORDER_STATUS", nullable = false, length = 20)
    private OrderStatus orderStatus;

    @Column(name = "ORDERED_AT", nullable = false)
    private LocalDateTime orderedAt;

    @Column(name = "PAYMENT_AT")
    private LocalDateTime paymentAt;

    @Column(name = "EXPIRES_AT")
    private LocalDateTime expiresAt;

}

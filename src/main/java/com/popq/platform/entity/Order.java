package com.popq.platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    private int totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "ORDER_STATUS", nullable = false, length = 20)
    private OrderStatus orderStatus;

    @Column(name = "ORDERED_AT", nullable = false)
    private LocalDateTime orderedAt;

    @Column(name = "PAYMENT_AT")
    private LocalDateTime paymentAt;

    @Column(name = "EXPIRES_AT")
    private LocalDateTime expiresAt;

    public void completePayment() {
        this.orderStatus = OrderStatus.PAID;
        this.paymentAt = LocalDateTime.now();
    }

    public void cancel() {
        this.orderStatus = OrderStatus.CANCELED;
    }

    public void expire() {
        this.orderStatus = OrderStatus.EXPIRED;
    }

    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<OrderItem> orderItems = new ArrayList<>();

    public void addOrderItem(OrderItem orderItem) {
        this.orderItems.add(orderItem);
        orderItem.assignOrder(this);
    }

    public static Order createOrder(User user, int totalPrice, LocalDateTime expiresAt){
        Order order = new Order();

        order.user = user;
        order.totalPrice = totalPrice;
        order.orderStatus = OrderStatus.PENDING;
        order.orderedAt = LocalDateTime.now();
        order.expiresAt = expiresAt;

        return order;
    }

}

package com.popq.platform.order.entity;

import com.popq.platform.auth.entity.User;
import com.popq.platform.coupon.entity.UserCoupon;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "ORDERS")
public class Order {

    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "orders_seq_gen"
    )
    @SequenceGenerator(
            name = "orders_seq_gen",
            sequenceName = "orders_seq",
            allocationSize = 1
    )
    @Column(name = "ORDER_ID")
    private Long orderId;

    @Column(name = "TOSS_ORDER_ID", unique = true, length = 20)
    private String tossOrderId;

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

    @Column(name = "RECEIVER_NAME")
    private String receiverName;

    @Column(name = "RECEIVER_PHONE")
    private String receiverPhone;

    @Column(name = "DELIVERY_ADDRESS")
    private String deliveryAddress;

    @Column(name = "DELIVERY_ADDRESS_DETAIL")
    private String deliveryAddressDetail;

    @Column(name = "DELIVERY_MEMO")
    private String deliveryMemo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_COUPON_ID")
    private UserCoupon userCoupon;

    public void completePayment() {
        this.orderStatus = OrderStatus.PAID;
        this.paymentAt = LocalDateTime.now();
    }

    public void cancel() {
        this.orderStatus = OrderStatus.CANCELLED;
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

    public static Order createOrder(
            User user, int totalPrice, LocalDateTime expiresAt,
            String receiverName, String receiverPhone, String deliveryAddress, String deliveryAddressDetail,
            String deliveryMemo, UserCoupon userCoupon
    )
    {
        Order order = new Order();

        order.user = user;
        order.totalPrice = totalPrice;
        order.orderStatus = OrderStatus.PENDING;
        order.orderedAt = LocalDateTime.now();
        order.expiresAt = expiresAt;

        order.receiverName = receiverName;
        order.receiverPhone = receiverPhone;
        order.deliveryAddress = deliveryAddress;
        order.deliveryAddressDetail = deliveryAddressDetail;
        order.deliveryMemo = deliveryMemo;

        order.userCoupon = userCoupon;

        order.tossOrderId = UUID.randomUUID()
                .toString()
                .replace("-", "");

        return order;
    }
}

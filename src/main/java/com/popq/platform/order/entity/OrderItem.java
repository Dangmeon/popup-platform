package com.popq.platform.order.entity;

import com.popq.platform.product.entity.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "ORDER_ITEM")
public class OrderItem {

    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "order_item_seq_gen"
    )
    @SequenceGenerator(
            name = "order_item_seq_gen",
            sequenceName = "order_item_seq",
            allocationSize = 1
    )
    @Column(name = "ORDER_ITEM_ID")
    private Long orderItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ORDER_ID", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PRD_ID", nullable = false)
    private Product product;

    @Column(name = "QUANTITY", nullable = false)
    private Integer quantity;

    @Column(name = "PRICE", nullable = false)
    private int price;

    @Column(name = "UNIT_PRICE", nullable = false)
    private int unitPrice;

    public void assignOrder(Order order) {
        this.order = order;
    }

    public static OrderItem createOrderItem(Product product, Integer quantity, int unitPrice, int price) {
        OrderItem orderItem = new OrderItem();

        orderItem.product = product;
        orderItem.quantity = quantity;
        orderItem.unitPrice = unitPrice;
        orderItem.price = price;

        return orderItem;
    }
}
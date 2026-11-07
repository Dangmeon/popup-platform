package com.popq.platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "CART")
@Getter
@Setter
@NoArgsConstructor
public class Cart {

    // 장바구니 번호
    @Id
    @SequenceGenerator(
            name = "cart_seq_gen",
            sequenceName = "CART_SEQ",
            allocationSize = 1
    )
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "cart_seq_gen"
    )
    @Column(name = "CART_ID")
    private Long cartId;

    // 회원 및 상품
    @Column(name = "MEMBER_ID", nullable = false)
    private Long memberId;

    @Column(name = "PRD_ID", nullable = false)
    private Long prdId;

    // 상품 수량
    @Column(name = "QUANTITY", nullable = false)
    private Integer quantity;

    // 등록 시각
    @Column(name = "ADDED_AT", nullable = false)
    private LocalDateTime addedAt;

    @PrePersist
    public void prePersist() {
        if (addedAt == null) {
            addedAt = LocalDateTime.now();
        }
    }
}
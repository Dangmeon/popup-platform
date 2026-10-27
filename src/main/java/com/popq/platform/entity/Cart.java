
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CART_ID")
    private Long cartId;

    @Column(name = "MEMBER_ID", nullable = false)
    private Long memberId;

    @Column(name = "PRD_ID", nullable = false)
    private Long prdId;

    @Column(name = "QUANTITY", nullable = false)
    private Integer quantity;

    @Column(name = "ADDED_AT")
    private LocalDateTime addedAt;

    @PrePersist
    public void prePersist() {
        if (addedAt == null) {
            addedAt = LocalDateTime.now();
        }
    }
}

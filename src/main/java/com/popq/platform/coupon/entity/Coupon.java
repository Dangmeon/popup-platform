package com.popq.platform.coupon.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;          // 쿠폰 이름
    private int discountAmount;   // 할인 금액
    private int stock;            // 발급 가능 수량
    private LocalDateTime expiredAt; // 쿠폰 만료 일시

    @Builder
    public Coupon(String name, int discountAmount, int stock, LocalDateTime expiredAt) {
        this.name = name;
        this.discountAmount = discountAmount;
        this.stock = stock;
        this.expiredAt = expiredAt;
    }

    public void decreaseStock() {
        if (this.stock <= 0) {
            throw new IllegalArgumentException("쿠폰이 모두 소진되었습니다.");
        }
        this.stock--;
    }
}
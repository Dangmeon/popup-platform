package com.popq.platform.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class UserCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id")
    private Coupon coupon;

    private boolean isUsed = false;       // 사용 여부 (0: 미사용, 1: 사용함)
    private LocalDateTime issuedAt;       // 발급 일시

    @Builder
    public UserCoupon(User user, Coupon coupon) {
        this.user = user;
        this.coupon = coupon;
        this.issuedAt = LocalDateTime.now();
    }

    // 쿠폰 사용 처리
    public void markAsUsed() {
        if (this.isUsed) {
            throw new IllegalArgumentException("이미 사용한 쿠폰입니다.");
        }
        this.isUsed = true;
    }
}
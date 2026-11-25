package com.popq.platform.coupon.dto;

import com.popq.platform.coupon.entity.Coupon;
import com.popq.platform.coupon.entity.UserCoupon;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserCouponResponse {

    private Long userCouponId;      // 발급된 쿠폰 고유 ID (결제 시 사용)
    private Long couponId;          // 원본 쿠폰 ID
    private String name;            // 쿠폰 이름
    private int discountAmount;     // 할인 금액
    private boolean isUsed;         // 사용 여부
    private LocalDateTime issuedAt; // 발급 일시
    private LocalDateTime expiredAt;// 만료 일시

    public static UserCouponResponse from(UserCoupon userCoupon) {
        Coupon coupon = userCoupon.getCoupon();
        return UserCouponResponse.builder()
                .userCouponId(userCoupon.getId())
                .couponId(coupon.getId())
                .name(coupon.getName())
                .discountAmount(coupon.getDiscountAmount())
                .isUsed(userCoupon.isUsed())
                .issuedAt(userCoupon.getIssuedAt())
                .expiredAt(coupon.getExpiredAt())
                .build();
    }
}
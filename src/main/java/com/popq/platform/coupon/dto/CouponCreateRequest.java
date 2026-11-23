package com.popq.platform.coupon.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class CouponCreateRequest {
    private String name;          // 쿠폰 이름
    private int discountAmount;   // 할인 금액
    private int stock;            // 발급 가능 수량
    private LocalDateTime expiredAt; // 쿠폰 만료 일시
}
package com.popq.platform.coupon.controller;

import com.popq.platform.coupon.dto.CouponCreateRequest;
import com.popq.platform.coupon.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 관리자 쿠폰 API (ADMIN만 접근, SecurityConfig에서 제한)
@RestController
@RequestMapping("/api/admin/coupons")
@RequiredArgsConstructor
public class AdminCouponController {

    private final CouponService couponService;

    // 관리자 쿠폰 생성 API
    @PostMapping
    public ResponseEntity<String> createCoupon(@RequestBody CouponCreateRequest requestDto) {
        couponService.createCoupon(requestDto);
        return ResponseEntity.ok("쿠폰이 성공적으로 생성되었습니다.");
    }
}

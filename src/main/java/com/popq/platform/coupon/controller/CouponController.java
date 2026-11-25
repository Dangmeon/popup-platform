package com.popq.platform.coupon.controller;

import com.popq.platform.coupon.dto.CouponCreateRequest;
import com.popq.platform.coupon.dto.UserCouponResponse;
import com.popq.platform.coupon.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    // 관리자 쿠폰 생성 API
    @PostMapping("/admin")
    public ResponseEntity<String> createCoupon(@RequestBody CouponCreateRequest requestDto) {
        couponService.createCoupon(requestDto);
        return ResponseEntity.ok("쿠폰이 성공적으로 생성되었습니다.");
    }

    // 사용자 쿠폰 발급 API
    @PostMapping("/{couponId}")
    public ResponseEntity<String> issueCoupon(
            @PathVariable Long couponId,
            @AuthenticationPrincipal String username) {
        couponService.issueCoupon(username, couponId);
        return ResponseEntity.ok("쿠폰이 성공적으로 발급되었습니다.");
    }

    // 내 쿠폰 목록 조회 API
    @GetMapping("/my")
    public ResponseEntity<List<UserCouponResponse>> getMyCoupons(
            @AuthenticationPrincipal String username) {
        return ResponseEntity.ok(couponService.getMyCoupons(username));
    }
}
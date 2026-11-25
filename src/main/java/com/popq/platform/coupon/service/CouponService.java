package com.popq.platform.coupon.service;

import com.popq.platform.auth.entity.User;
import com.popq.platform.auth.repository.UserRepository;
import com.popq.platform.coupon.dto.CouponCreateRequest;
import com.popq.platform.coupon.dto.UserCouponResponse;
import com.popq.platform.coupon.entity.Coupon;
import com.popq.platform.coupon.entity.UserCoupon;
import com.popq.platform.coupon.repository.CouponRepository;
import com.popq.platform.coupon.repository.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;

    // 관리자 쿠폰 생성
    @Transactional
    public Long createCoupon(CouponCreateRequest requestDto) {
        Coupon coupon = Coupon.builder()
                .name(requestDto.getName())
                .discountAmount(requestDto.getDiscountAmount())
                .stock(requestDto.getStock())
                .expiredAt(requestDto.getExpiredAt())
                .build();

        return couponRepository.save(coupon).getId();
    }

    // 사용자 쿠폰 발급
    @Transactional
    public void issueCoupon(String username, Long couponId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 쿠폰입니다."));

        // 중복 발급 방어
        if (userCouponRepository.existsByUserAndCoupon(user, coupon)) {
            throw new IllegalArgumentException("이미 발급받은 쿠폰입니다.");
        }

        // 재고 차감
        coupon.decreaseStock();

        // 발급 내역 저장
        UserCoupon userCoupon = UserCoupon.builder()
                .user(user)
                .coupon(coupon)
                .build();

        userCouponRepository.save(userCoupon);
    }

    // 내 쿠폰 목록 조회 (마이페이지)
    @Transactional(readOnly = true)
    public List<UserCouponResponse> getMyCoupons(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        return userCouponRepository.findAllByUserWithCoupon(user).stream()
                .map(UserCouponResponse::from)
                .toList();
    }

    // 주문 적용
    @Transactional(readOnly = true)
    public int calculateDiscountedPrice(String username, Long userCouponId, int totalPrice) {
        UserCoupon userCoupon = validateAndGetUserCoupon(username, userCouponId);

        int discountedPrice = totalPrice - userCoupon.getCoupon().getDiscountAmount();
        return Math.max(0, discountedPrice);
    }

    // 결제 성공 시 호출
    @Transactional
    public void useCoupon(String username, Long userCouponId) {
        UserCoupon userCoupon = validateAndGetUserCoupon(username, userCouponId);
        userCoupon.markAsUsed();
    }

    // 공통 검증 로직
    private UserCoupon validateAndGetUserCoupon(String username, Long userCouponId) {
        UserCoupon userCoupon = userCouponRepository.findById(userCouponId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 발급 쿠폰입니다."));

        if (!userCoupon.getUser().getUsername().equals(username)) {
            throw new IllegalArgumentException("본인 소유의 쿠폰이 아닙니다.");
        }

        if (userCoupon.isUsed()) {
            throw new IllegalArgumentException("이미 사용한 쿠폰입니다.");
        }

        LocalDateTime expiredAt = userCoupon.getCoupon().getExpiredAt();
        if (expiredAt != null && LocalDateTime.now().isAfter(expiredAt)) {
            throw new IllegalArgumentException("만료된 쿠폰입니다.");
        }

        return userCoupon;
    }

    @Transactional(readOnly = true)
    public UserCoupon getValidUserCoupon(
            String username,
            Long userCouponId
    ) {
        return validateAndGetUserCoupon(username, userCouponId);
    }
}
package com.popq.platform.service;

import com.popq.platform.dto.CouponCreateRequest;
import com.popq.platform.entity.Coupon;
import com.popq.platform.entity.User;
import com.popq.platform.entity.UserCoupon;
import com.popq.platform.repository.CouponRepository;
import com.popq.platform.repository.UserCouponRepository;
import com.popq.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;

    // 관리자 쿠폰 발급
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
}
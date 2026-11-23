package com.popq.platform.coupon.repository;

import com.popq.platform.coupon.entity.Coupon;
import com.popq.platform.auth.entity.User;
import com.popq.platform.coupon.entity.UserCoupon;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {
    // 이미 해당 유저가 이 쿠폰을 발급받았는지 중복 체크용
    boolean existsByUserAndCoupon(User user, Coupon coupon);
}
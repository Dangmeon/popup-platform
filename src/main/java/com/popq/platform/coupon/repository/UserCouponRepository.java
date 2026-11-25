package com.popq.platform.coupon.repository;

import com.popq.platform.auth.entity.User;
import com.popq.platform.coupon.entity.Coupon;
import com.popq.platform.coupon.entity.UserCoupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {
    // 이미 해당 유저가 이 쿠폰을 발급받았는지 중복 체크용
    boolean existsByUserAndCoupon(User user, Coupon coupon);

    // 마이페이지 내 쿠폰 목록 조회용
    @Query("select uc from UserCoupon uc join fetch uc.coupon where uc.user = :user order by uc.issuedAt desc")
    List<UserCoupon> findAllByUserWithCoupon(@Param("user") User user);
}
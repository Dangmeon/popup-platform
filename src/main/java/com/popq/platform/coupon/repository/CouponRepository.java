package com.popq.platform.coupon.repository;

import com.popq.platform.coupon.entity.Coupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    // 쿠폰 발급용 비관적 락 조회
    // 같은 쿠폰을 동시에 발급하려는 요청은 앞 트랜잭션이 끝날 때까지 대기
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.id = :couponId")
    Optional<Coupon> findByIdForUpdate(@Param("couponId") Long couponId);
}
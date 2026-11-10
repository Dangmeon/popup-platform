package com.popq.platform.repository;

import com.popq.platform.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    // 회원 장바구니 조회
    List<Cart> findByMemberId(Long memberId);

    // 최근 등록 순서 조회
    List<Cart> findByMemberIdOrderByAddedAtDescCartIdDesc(Long memberId);

    // 기존에 담은 상품 조회
    Optional<Cart> findByMemberIdAndPrdId(
            Long memberId,
            Long prdId
    );
}
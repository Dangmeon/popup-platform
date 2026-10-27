
package com.popq.platform.repository;

import com.popq.platform.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    // 특정 회원의 장바구니 전체 조회
    List<Cart> findByMemberId(Long memberId);

    // 회원이 이미 담은 상품인지 확인
    Optional<Cart> findByMemberIdAndPrdId(Long memberId, Long prdId);
}

package com.popq.platform.repository;

import com.popq.platform.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    // 팝업의 상품 목록 (등록순)
    List<Product> findByPopupStore_PopIdOrderByPrdIdAsc(Long popId);

    // 팝업에 상품이 하나라도 있는지 (팝업 삭제 전 확인용)
    boolean existsByPopupStore_PopId(Long popId);

    // [주문 연동] 주문 생성 시 재고 차감 - 재고가 충분할 때만 (조건부 UPDATE)
    // 결과 1 : 성공 / 0 : 품절 또는 재고 부족
    // 반드시 @Transactional 안에서 호출
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Product p SET p.stock = p.stock - :quantity WHERE p.prdId = :prdId AND p.stock >= :quantity")
    int decreaseStock(@Param("prdId") Long prdId, @Param("quantity") int quantity);

    // [주문 연동] 주문 취소/만료 시 재고 복구
    // 결과 1 : 성공 / 0 : 없는 상품
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Product p SET p.stock = p.stock + :quantity WHERE p.prdId = :prdId")
    int increaseStock(@Param("prdId") Long prdId, @Param("quantity") int quantity);
}

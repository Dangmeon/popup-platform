package com.popq.platform.repository;

import com.popq.platform.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    // 팝업의 상품 목록 (등록순)
    @Query("SELECT p FROM Product p WHERE p.popupStore.popId = :popId ORDER BY p.prdId ASC")
    List<Product> findByPopId(@Param("popId") Long popId);

    // 관리자 상품 목록 : 값이 null인 조건은 건너뜀 (managerId null = 전체, popId null = 모든 팝업, keyword null = 검색 안 함)
    // JOIN FETCH : 팝업 정보(팝업명, 상태)를 한 번에 같이 가져옴 (상품마다 팝업을 따로 조회하지 않도록)
    // 정렬 : 팝업 시작일 최신순 -> 같은 팝업 안에서는 상품 등록순
    @Query("SELECT p FROM Product p JOIN FETCH p.popupStore s LEFT JOIN s.manager m "
            + "WHERE (:managerId IS NULL OR m.id = :managerId) "
            + "AND (:popId IS NULL OR s.popId = :popId) "
            + "AND (:keyword IS NULL OR LOCATE(:keyword, p.productName) > 0) "
            + "ORDER BY s.startDate DESC, p.prdId ASC")
    List<Product> findAdminList(@Param("managerId") Long managerId, @Param("popId") Long popId, @Param("keyword") String keyword);

    // 팝업에 상품이 하나라도 있는지 (팝업 삭제 전 확인용)
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Product p WHERE p.popupStore.popId = :popId")
    boolean existsByPopId(@Param("popId") Long popId);

    // 팝업 종료일 수정 전 확인용 : 판매 시작이 기준 시각보다 빠른 상품이 있는지
    // 기준 시각 = 새 종료일 다음날 00:00 -> 판매 시작일이 새 종료일 당일이거나 그 전이면 true
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Product p"
            + " WHERE p.popupStore.popId = :popId AND p.saleStart < :dateTime")
    boolean existsSaleStartBefore(@Param("popId") Long popId, @Param("dateTime") LocalDateTime dateTime);

    // [주문 연동] 주문 생성 시 재고 차감 - 재고가 충분할 때만 (조건부 UPDATE)
    // 결과 1 : 성공 / 0 : 품절 또는 재고 부족
    // 반드시 @Transactional 안에서 호출
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Product p SET p.stock = p.stock - :quantity WHERE p.prdId = :prdId AND p.stock >= :quantity")
    int decreaseStock(@Param("prdId") Long prdId, @Param("quantity") int quantity);

    // [주문 연동] 주문 취소/만료 시 재고 복구
    // 결과 1 : 성공 / 0 : 없는 상품
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Product p SET p.stock = p.stock + :quantity WHERE p.prdId = :prdId")
    int increaseStock(@Param("prdId") Long prdId, @Param("quantity") int quantity);
}
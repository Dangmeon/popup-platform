package com.popq.platform.repository;

import com.popq.platform.entity.PopupStoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PopupStoreRepository extends JpaRepository<PopupStoreEntity, Long>  {
    // 진행중인 팝업 조회 : 시작일 <= 오늘 <= 종료일
    @Query(value = "SELECT p FROM PopupStoreEntity p "
            + "WHERE p.startDate <= :today AND p.endDate >= :today "
            + "ORDER BY p.startDate DESC")
    List<PopupStoreEntity> findOngoing(@Param("today") LocalDate today);

    // 카테고리별 조회 (메소드 이름만으로 쿼리 자동 생성)
    List<PopupStoreEntity> findByCategoryOrderByStartDateDesc(String category);
}

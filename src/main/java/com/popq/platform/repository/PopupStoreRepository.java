package com.popq.platform.repository;

import com.popq.platform.entity.PopupStore;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PopupStoreRepository extends JpaRepository<PopupStore, Long>  {
    // 진행중인 팝업 조회 : 시작일 <= 오늘 <= 종료일
    @Query("SELECT p FROM PopupStore p WHERE p.startDate <= :today AND p.endDate >= :today ORDER BY p.startDate DESC")
    List<PopupStore> findOngoing(@Param("today") LocalDate today);

    // 카테고리별 조회 (메소드 이름만으로 쿼리 자동 생성)
    List<PopupStore> findByCategoryOrderByStartDateDesc(String category);

    // [웨이팅 연동] 비관적 락 조회 - 대기번호 중복 발급 방지
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PopupStore p WHERE p.popId = :popId")
    Optional<PopupStore> findByIdWithPessimisticLock(@Param("popId") Long popId);
}

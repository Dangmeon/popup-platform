package com.popq.platform.popup.repository;

import com.popq.platform.popup.entity.PopupStore;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PopupStoreRepository extends JpaRepository<PopupStore, Long>  {
    // 관리자 팝업 목록 : 값이 null인 조건은 건너뜀 (managerId null = 전체, keyword null = 검색 안 함, opStatus null = 전체 상태)
    // keyword : LOCATE(찾을 글자, 컬럼) > 0 = 그 글자가 포함되어 있음 (Oracle에서는 INSTR로 바뀜)
    @Query("SELECT p FROM PopupStore p LEFT JOIN p.manager m "
            + "WHERE (:managerId IS NULL OR m.id = :managerId) "
            + "AND (:keyword IS NULL OR LOCATE(:keyword, p.title) > 0 OR LOCATE(:keyword, p.location) > 0) "
            + "AND (:opStatus IS NULL OR p.opStatus = :opStatus) "
            + "ORDER BY p.startDate DESC")
    List<PopupStore> findAdminList(@Param("managerId") Long managerId,
                                   @Param("keyword") String keyword,
                                   @Param("opStatus") String opStatus);

    // [웨이팅 연동] 비관적 락 조회 - 대기번호 중복 발급 방지
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PopupStore p WHERE p.popId = :popId")
    Optional<PopupStore> findByIdWithPessimisticLock(@Param("popId") Long popId);

    // 팅장 작성 ----

    // 전체 조회에서 CANCELLED 상태만 제외하고 모두 찾는 쿼리
    @Query("SELECT p FROM PopupStore p WHERE p.opStatus <> 'CANCELLED' ORDER BY p.startDate DESC")
    List<PopupStore> findAllActive();

    // [찜/인기순 연동] 사용자 팝업 목록 통합 조회 (keyword / status / category / sort=POPULAR 동시 조합 지원)
    // keyword : 팝업명·장소에 포함 (LOCATE > 0 = 포함, Oracle에서는 INSTR로 바뀜)
    // status : UPCOMING(오픈 예정), ONGOING(진행 중), ENDED(종료)
    @Query("""
        SELECT p
        FROM PopupStore p
        WHERE p.opStatus <> 'CANCELLED'
          AND (:keyword IS NULL OR LOCATE(:keyword, p.title) > 0 OR LOCATE(:keyword, p.location) > 0)
          AND (:status IS NULL
               OR (:status = 'UPCOMING' AND p.startDate > :today)
               OR (:status = 'ONGOING' AND p.startDate <= :today AND p.endDate >= :today)
               OR (:status = 'ENDED' AND p.endDate < :today))
          AND (:category IS NULL OR p.category = :category)
        ORDER BY
          CASE WHEN :sort = 'POPULAR' THEN (
              SELECT COUNT(f)
              FROM Favorite f
              WHERE f.popupStore = p AND f.targetType = 'POPUP'
          ) END DESC,
          p.startDate DESC
    """)
    List<PopupStore> findUserPopups(@Param("keyword") String keyword,
                                    @Param("status") String status,
                                    @Param("category") String category,
                                    @Param("sort") String sort,
                                    @Param("today") LocalDate today);

    // 팝업 삭제 전 : 이 팝업의 찜(favorite)을 함께 삭제 (FK 때문에 남아 있으면 팝업 삭제 실패)
    @Modifying
    @Query("DELETE FROM Favorite f WHERE f.popupStore.popId = :popId")
    int deleteFavoritesByPopId(@Param("popId") Long popId);
}

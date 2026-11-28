package com.popq.platform.favorite.repository;

import com.popq.platform.favorite.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    // 단건 찜 데이터 조회 (찜 등록 시 중복 여부 체크, 찜 취소 시 삭제할 엔티티 찾기용)
    @Query("SELECT f FROM Favorite f WHERE f.member.id = :memberId AND f.popupStore.popId = :popId AND f.targetType = 'POPUP'")
    Optional<Favorite> findPopupFavorite(@Param("memberId") Long memberId, @Param("popId") Long popId);

    // 팝업 상세 조회용 : 로그인한 회원이 해당 팝업을 찜했는지 여부만 확인
    @Query("SELECT COUNT(f) > 0 FROM Favorite f WHERE f.member.id = :memberId AND f.popupStore.popId = :popId AND f.targetType = 'POPUP'")
    boolean isPopupFavorited(@Param("memberId") Long memberId, @Param("popId") Long popId);

    // 팝업 상세 조회용 : 해당 팝업이 받은 총 찜 개수 카운트
    @Query("SELECT COUNT(f) FROM Favorite f WHERE f.popupStore.popId = :popId AND f.targetType = 'POPUP'")
    long countPopupFavorites(@Param("popId") Long popId);

    // [N+1 방지] 팝업 목록 조회용 : 화면에 뿌릴 팝업 ID 목록 중 내가 찜한 popId만 Set으로 한 번에 조회
    // 예)
    //    - 만약 팝업 20개마다 isPopupFavorited()를 호출하면 쿼리가 20번(N+1) 나가지만,
    //    - IN 절을 사용하면 쿼리 단 1번으로 20개 팝업의 내 찜 여부를 모두 판별할 수 있다.
    @Query("SELECT f.popupStore.popId FROM Favorite f WHERE f.member.id = :memberId AND f.targetType = 'POPUP' AND f.popupStore.popId IN :popIds")
    Set<Long> findFavoritedPopIds(@Param("memberId") Long memberId, @Param("popIds") List<Long> popIds);

    // [N+1 방지] 팝업 목록 조회용 : 팝업 ID 목록별 총 찜 개수를 GROUP BY로 한 번에 집계
    // 예)
    //    - 반환 형태 : [popId(Long), 찜개수(Long)] 형태의 Object[] 리스트
    //    - 찜이 0개인 팝업은 GROUP BY 결과에 포함되지 않으므로 서비스에서 getOrDefault(popId, 0L)로 0 처리
    @Query("SELECT f.popupStore.popId, COUNT(f) FROM Favorite f WHERE f.targetType = 'POPUP' AND f.popupStore.popId IN :popIds GROUP BY f.popupStore.popId")
    List<Object[]> countFavoritesByPopIds(@Param("popIds") List<Long> popIds);

    @Query("SELECT f FROM Favorite f " +
            "JOIN FETCH f.popupStore p " +
            "WHERE f.member.username = :email " +
            "AND f.targetType = 'POPUP' " +
            "ORDER BY f.createdAt DESC")
    List<Favorite> findAllByEmailWithPopup(@Param("email") String email);
}
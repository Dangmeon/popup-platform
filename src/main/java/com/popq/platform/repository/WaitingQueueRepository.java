package com.popq.platform.repository;

import com.popq.platform.entity.WaitingQueue;
import com.popq.platform.entity.WaitingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WaitingQueueRepository extends JpaRepository<WaitingQueue, Long> { // WaitingQueue와 PK waitingId

    // 중복 대기 방지
    @Query("SELECT CASE WHEN COUNT(w) > 0 THEN true ELSE false END " +
            "FROM WaitingQueue w " +
            "WHERE w.popupStore.popId = :popId " +
            "  AND w.phone = :phone " +
            "  AND w.waitingStatus IN :statuses")
    boolean existsActiveWaiting(
            @Param("popId") Long popId, // 팝업 번호
            @Param("phone") String phone, // 연락처
            @Param("statuses") Collection<WaitingStatus> statuses // 상태값 ex) WAITING
    ); // 3개 값중 하나라도 null이 발생할 경우 false 반환
    // true일 경우 '이미 접수된 대기표가 있습니다.' 예외처리

    // 당일 마지막 대기번호 조회
    @Query("SELECT MAX(w.queueNumber) FROM WaitingQueue w " +
            "WHERE w.popupStore.popId = :popId AND w.joinedAt >= :startOfDay") // 해당 팝업에서 자정 이후로 계산된 대기 번호 중 가장 큰 번호를 찾는 쿼리
    Optional<Integer> findMaxQueueNumberToday(
            @Param("popId") Long popId, // 팝업아이디
            @Param("startOfDay") LocalDateTime startOfDay // 자정을 나타냄
    );

    // 내 앞에 몇 팀이 있는지? 계산
    // 예)
    // 1 : ENTERED      2 : CALLED
    // 3 : CENCELLED    4: WAITING
    // 5 : WAITING      6(me): WAITING
    // = 2 반환

    @Query("SELECT COUNT(w) FROM WaitingQueue w " +
            "WHERE w.popupStore.popId = :popId " +
            "  AND w.waitingStatus = :status " +
            "  AND w.queueNumber < :queueNumber " +
            "  AND w.joinedAt >= :startOfDay") // 해당 팝업에서 WAITING 상태이면서 번호가 더 작은 사람들을 카운팅하는 쿼리
    long countTeamsAhead(
            @Param("popId") Long popId,
            @Param("status") WaitingStatus status,
            @Param("queueNumber") Integer queueNumber,
            @Param("startOfDay") LocalDateTime startOfDay
    );


    // 관리자용: 특정 팝업 대기자 리스트 조회 용도 / 대기자의 상태를 대기번호 기준 오름차순으로 보여줌
    // WAITING CALLED 상태의 고객만 뽑아 대기 번호 기준 오름차순으로 보여줄 예정
    // 가독성을 위한 jpql 처리
    @Query("SELECT w FROM WaitingQueue w " +
            "WHERE w.popupStore.popId = :popId " +
            "  AND w.waitingStatus IN :statuses " +
            "ORDER BY w.queueNumber ASC")
    List<WaitingQueue> findActiveWaitingsByPopup(
            @Param("popId") Long popId,
            @Param("statuses") Collection<WaitingStatus> statuses
    );

    // 1분마다 체크하여 만료되었는지 아닌지 확인하는 용도
    // 예)
    // 만약 현재 시간이 14:06분이고 CALLED 상태의 고객의 입장 호출 시각이 13:55일때 만료기한(expired_at)이 10분일 경우 14:05분에 만료가 되어
    // EXPIRED 상태로 변경 후 큐에서 제거
    // jpql 처리
    @Query("SELECT w FROM WaitingQueue w " +
            "WHERE w.waitingStatus = :status " +
            "  AND w.expiresAt < :now")
    List<WaitingQueue> findExpiredWaitings(
            @Param("status") WaitingStatus status,
            @Param("now") LocalDateTime now
    );
}
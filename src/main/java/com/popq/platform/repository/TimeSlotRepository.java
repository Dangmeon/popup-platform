package com.popq.platform.repository;

import com.popq.platform.entity.TimeSlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {
    // 팝업의 전체 회차 (날짜, 시작시간 순)
    @Query("SELECT t FROM TimeSlot t WHERE t.popupStore.popId = :popId ORDER BY t.slotDate ASC, t.startTime ASC")
    List<TimeSlot> findByPopId(@Param("popId") Long popId);

    // 팝업의 특정 날짜 회차 (시작시간 순)
    @Query("SELECT t FROM TimeSlot t WHERE t.popupStore.popId = :popId AND t.slotDate = :slotDate ORDER BY t.startTime ASC")
    List<TimeSlot> findByPopIdAndDate(@Param("popId") Long popId, @Param("slotDate") LocalDate slotDate);

    // 같은 팝업 + 같은 날짜에 시간이 겹치는 회차가 있는지 (등록할 때)
    // 겹침 조건 : 기존 시작 < 새 끝 그리고 기존 끝 > 새 시작 (HH:mm 문자열이라 크기 비교 가능)
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM TimeSlot t"
            + " WHERE t.popupStore.popId = :popId AND t.slotDate = :slotDate"
            + " AND t.startTime < :endTime AND t.endTime > :startTime")
    boolean existsOverlap(@Param("popId") Long popId, @Param("slotDate") LocalDate slotDate,
                          @Param("startTime") String startTime, @Param("endTime") String endTime);

    // 위와 같지만 자기 자신은 빼고 확인 (수정할 때)
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM TimeSlot t"
            + " WHERE t.popupStore.popId = :popId AND t.slotDate = :slotDate"
            + " AND t.startTime < :endTime AND t.endTime > :startTime AND t.timeSlotId <> :timeSlotId")
    boolean existsOverlapExceptSelf(@Param("popId") Long popId, @Param("slotDate") LocalDate slotDate,
                                    @Param("startTime") String startTime, @Param("endTime") String endTime,
                                    @Param("timeSlotId") Long timeSlotId);

    // 팝업에 회차가 하나라도 있는지 (팝업 삭제 전 확인용)
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM TimeSlot t WHERE t.popupStore.popId = :popId")
    boolean existsByPopId(@Param("popId") Long popId);

    // 팝업 기간 수정 전 확인용 : 새 시작일보다 앞에 있는 회차가 있는지
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM TimeSlot t"
            + " WHERE t.popupStore.popId = :popId AND t.slotDate < :startDate")
    boolean existsBeforeStartDate(@Param("popId") Long popId, @Param("startDate") LocalDate startDate);

    // 팝업 기간 수정 전 확인용 : 새 종료일보다 뒤에 있는 회차가 있는지
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM TimeSlot t"
            + " WHERE t.popupStore.popId = :popId AND t.slotDate > :endDate")
    boolean existsAfterEndDate(@Param("popId") Long popId, @Param("endDate") LocalDate endDate);

    // 팝업 예약 시간 수정 전 확인용 : 새 예약 시작 시간보다 일찍 시작하는 회차가 있는지 (HH:mm 문자열 비교)
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM TimeSlot t"
            + " WHERE t.popupStore.popId = :popId AND t.startTime < :reservStart")
    boolean existsBeforeReservStart(@Param("popId") Long popId, @Param("reservStart") String reservStart);

    // 팝업 예약 시간 수정 전 확인용 : 새 예약 종료 시간보다 늦게 끝나는 회차가 있는지
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM TimeSlot t"
            + " WHERE t.popupStore.popId = :popId AND t.endTime > :reservEnd")
    boolean existsAfterReservEnd(@Param("popId") Long popId, @Param("reservEnd") String reservEnd);

    // [예약 연동] 비관적 락 조회
    // 같은 회차를 동시에 수정하려는 요청은 앞 트랜잭션이 끝날 때까지 대기
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TimeSlot t WHERE t.timeSlotId = :timeSlotId")
    Optional<TimeSlot> findByIdForUpdate(@Param("timeSlotId") Long timeSlotId);
}
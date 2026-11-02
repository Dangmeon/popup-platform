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
    List<TimeSlot> findByPopupStore_PopIdOrderBySlotDateAscStartTimeAsc(Long popId);

    // 팝업의 특정 날짜 회차 (시작시간 순)
    List<TimeSlot> findByPopupStore_PopIdAndSlotDateOrderByStartTimeAsc(Long popId, LocalDate slotDate);

    // 같은 팝업 + 같은 날짜에 시간이 겹치는 회차가 있는지 (등록할 때)
    // 겹침 조건 : 기존 시작 < 새 끝 그리고 기존 끝 > 새 시작 (HH:mm 문자열이라 크기 비교 가능)
    boolean existsByPopupStore_PopIdAndSlotDateAndStartTimeLessThanAndEndTimeGreaterThan(
            Long popId, LocalDate slotDate, String endTime, String startTime);

    // 위와 같지만 자기 자신은 빼고 확인 (수정할 때)
    boolean existsByPopupStore_PopIdAndSlotDateAndStartTimeLessThanAndEndTimeGreaterThanAndTimeSlotIdNot(
            Long popId, LocalDate slotDate, String endTime, String startTime, Long timeSlotId);

    // 팝업에 회차가 하나라도 있는지 (팝업 삭제 전 확인용)
    boolean existsByPopupStore_PopId(Long popId);

    // [예약 연동] 비관적 락 조회
    // 같은 회차를 동시에 수정하려는 요청은 앞 트랜잭션이 끝날 때까지 대기
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TimeSlot t WHERE t.timeSlotId = :timeSlotId")
    Optional<TimeSlot> findByIdForUpdate(@Param("timeSlotId") Long timeSlotId);
}

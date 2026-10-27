package com.popq.platform.repository;

import com.popq.platform.entity.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {
    // 팝업의 전체 회차 (날짜, 시작시간 순)
    List<TimeSlot> findByPopupStore_PopIdOrderBySlotDateAscStartTimeAsc(Long popId);

    // 팝업의 특정 날짜 회차 (시작시간 순)
    List<TimeSlot> findByPopupStore_PopIdAndSlotDateOrderByStartTimeAsc(Long popId, LocalDate slotDate);

    // 같은 팝업 + 같은 날짜 + 같은 시작시간 회차가 이미 있는지 (등록할 때)
    boolean existsByPopupStore_PopIdAndSlotDateAndStartTime(Long popId, LocalDate slotDate, String startTime);

    // 위와 같지만 자기 자신은 빼고 확인 (수정할 때)
    boolean existsByPopupStore_PopIdAndSlotDateAndStartTimeAndTimeSlotIdNot(
            Long popId, LocalDate slotDate, String startTime, Long timeSlotId);

    // 팝업에 회차가 하나라도 있는지 (팝업 삭제 전 확인용)
    boolean existsByPopupStore_PopId(Long popId);

    // [예약 연동] 예약 확정 시 인원 증가 - 정원을 넘지 않을 때만 증가 (조건부 UPDATE)
    // 결과 1 : 성공 / 0 : 정원 마감
    // 반드시 @Transactional 안에서 호출
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE TimeSlot t SET t.reservedCount = t.reservedCount + :count WHERE t.timeSlotId = :timeSlotId AND t.reservedCount + :count <= t.capacity")
    int increaseReservedCount(@Param("timeSlotId") Long timeSlotId, @Param("count") int count);

    // [예약 연동] 예약 취소 시 인원 감소 - 0 밑으로 내려가지 않을 때만 감소 (조건부 UPDATE)
    // 결과 1 : 성공 / 0 : 감소할 인원이 없음 (이상 상황)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE TimeSlot t SET t.reservedCount = t.reservedCount - :count WHERE t.timeSlotId = :timeSlotId AND t.reservedCount - :count >= 0")
    int decreaseReservedCount(@Param("timeSlotId") Long timeSlotId, @Param("count") int count);
}

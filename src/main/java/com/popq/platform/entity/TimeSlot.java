package com.popq.platform.entity;

import com.popq.platform.dto.TimeSlotRequest;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "time_slot")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeSlot {

    // 예약 오픈 : 회차 날짜 7일 전 10:00 / 예약 마감 : 회차 날짜 하루 전까지 (당일 0시에 마감)
    public static final int RESERV_OPEN_DAYS_BEFORE = 7;
    public static final LocalTime RESERV_OPEN_TIME = LocalTime.of(10, 0); // 오픈 시각 확정되면 여기만 수정

    @Id
    @SequenceGenerator(name = "time_slot_seq_gen", sequenceName = "time_slot_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "time_slot_seq_gen")
    @Column(name = "time_slot_id")
    private Long timeSlotId;

    // 어느 팝업의 회차인지 (time_slot.pop_id -> popup_store.pop_id FK)
    // LAZY : 회차를 조회할 때 팝업 정보까지 매번 같이 SELECT 하지 않음
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pop_id")
    private PopupStore popupStore;

    @Column(name = "slot_num")
    private Integer slotNum;

    @Column(name = "slot_date")
    private LocalDate slotDate;

    @Column(name = "start_time")
    private String startTime;

    @Column(name = "end_time")
    private String endTime;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "reserved_count")
    private Integer reservedCount;

    // Request -> Entity 변환시 사용
    // 등록 : timeSlotId = null, reservedCount = 0
    // 수정 : timeSlotId = 수정할 회차 번호, reservedCount = DB에 있던 값 그대로 (덮어쓰면 안 됨)
    public static TimeSlot toEntity(Long timeSlotId, PopupStore popupStore, TimeSlotRequest request, Integer reservedCount) {
        return TimeSlot.builder()
            .timeSlotId(timeSlotId)
            .popupStore(popupStore)
            .slotNum(request.getSlotNum())
            .slotDate(request.getSlotDate())
            .startTime(request.getStartTime())
            .endTime(request.getEndTime())
            .capacity(request.getCapacity())
            .reservedCount(reservedCount)
            .build();
    }

    // [예약 연동] 예약 인원 증가 / 감소
    // 반드시 TimeSlotRepository.findByIdForUpdate()로 락을 걸고 조회한 회차에서 호출
    public void increaseReservedCount(int count) {
        if (reservedCount + count > capacity) {
            throw new IllegalStateException("남은 자리가 부족합니다. (남은 자리: " + (capacity - reservedCount) + "명)");
        }
        this.reservedCount += count;
    }

    public void decreaseReservedCount(int count) {
        if (reservedCount - count < 0) {
            throw new IllegalStateException("예약 인원이 0명 미만이 될 수 없습니다.");
        }
        this.reservedCount -= count;
    }
}

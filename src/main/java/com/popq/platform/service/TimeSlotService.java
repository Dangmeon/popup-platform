package com.popq.platform.service;

import com.popq.platform.dto.TimeSlotGenerateRequest;
import com.popq.platform.dto.TimeSlotRequest;
import com.popq.platform.dto.TimeSlotResponse;
import com.popq.platform.entity.PopupStore;
import com.popq.platform.entity.TimeSlot;
import com.popq.platform.repository.PopupStoreRepository;
import com.popq.platform.repository.ReservationRepository;
import com.popq.platform.repository.TimeSlotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true) // 기본 읽기 전용, 쓰기 메소드에만 @Transactional
public class TimeSlotService {

    private static final String TIME_REGEX = "^([01]\\d|2[0-3]):[0-5]\\d$"; // HH:mm

    TimeSlotRepository timeSlotRepo;
    PopupStoreRepository popupRepo;
    ReservationRepository reservationRepo;

    @Autowired
    public TimeSlotService(TimeSlotRepository timeSlotRepo, PopupStoreRepository popupRepo, ReservationRepository reservationRepo) {
        this.timeSlotRepo = timeSlotRepo;
        this.popupRepo = popupRepo;
        this.reservationRepo = reservationRepo;
    }

    // 회차 목록 (date가 있으면 그 날짜만)
    public List<TimeSlotResponse> listTimeSlot(Long popId, LocalDate date) {
        List<TimeSlot> entityList;
        if (date != null) {
            entityList = timeSlotRepo.findByPopupStore_PopIdAndSlotDateOrderByStartTimeAsc(popId, date);
        } else {
            entityList = timeSlotRepo.findByPopupStore_PopIdOrderBySlotDateAscStartTimeAsc(popId);
        }
        return toDtoList(entityList);
    }

    public TimeSlotResponse detailTimeSlot(Long timeSlotId) {
        return TimeSlotResponse.toDto(findTimeSlot(timeSlotId));
    }

    // 회차 1개 등록 : 등록된 회차 정보 반환
    @Transactional
    public TimeSlotResponse insertTimeSlot(Long popId, TimeSlotRequest request) { // popId는 URL에서 받음
        PopupStore popup = findPopup(popId);
        checkTimeSlot(popup, request);
        if (timeSlotRepo.existsByPopupStore_PopIdAndSlotDateAndStartTimeLessThanAndEndTimeGreaterThan(
                popup.getPopId(), request.getSlotDate(), request.getEndTime(), request.getStartTime())) {
            throw new IllegalArgumentException("같은 날짜에 시간이 겹치는 회차가 이미 있습니다.");
        }
        TimeSlot saved = timeSlotRepo.save(TimeSlot.toEntity(null, popup, request, 0));
        return TimeSlotResponse.toDto(saved); // timeSlotId 대신 회차 정보 전체 반환
    }

    // 자동 생성 : 생성된 개수 반환
    // 예) 10:00~16:00, 60분, 점심 12:30~13:30 → 10:00, 11:00, 13:30, 14:30
    @Transactional
    public int generateTimeSlots(Long popId, TimeSlotGenerateRequest request) { // popId는 URL에서 받음
        PopupStore popup = findPopup(popId);
        fillDefaults(popup, request);
        checkGenerate(popup, request);

        List<TimeSlot> newSlots = new ArrayList<TimeSlot>();
        for (LocalDate date = request.getStartDate(); !date.isAfter(request.getEndDate()); date = date.plusDays(1)) {
            newSlots.addAll(createDaySlots(popup, date, request));
        }
        timeSlotRepo.saveAll(newSlots); // 전체 기간을 한 번에 저장
        return newSlots.size();
    }

    // 예약 이력이 있으면(취소, 만료 포함) 날짜/시간은 못 바꾸고 정원만 변경 가능
    @Transactional
    public void updateTimeSlot(Long timeSlotId, TimeSlotRequest request) {
        TimeSlot slot = findTimeSlotForUpdate(timeSlotId); // 락 : 수정 중에 예약 인원이 바뀌지 않도록
        PopupStore popup = slot.getPopupStore();
        checkTimeSlot(popup, request);

        if (reservationRepo.existsByTimeSlot_TimeSlotId(timeSlotId)) {
            boolean changed = !slot.getSlotDate().equals(request.getSlotDate())
                    || !slot.getStartTime().equals(request.getStartTime())
                    || !slot.getEndTime().equals(request.getEndTime());
            if (changed) {
                throw new IllegalArgumentException("예약 이력이 있는 회차는 날짜와 시간을 바꿀 수 없습니다. 정원만 변경 가능합니다.");
            }
        }
        if (request.getCapacity() < slot.getReservedCount()) {
            throw new IllegalArgumentException("정원은 현재 예약 인원(" + slot.getReservedCount() + "명)보다 적을 수 없습니다.");
        }
        if (timeSlotRepo.existsByPopupStore_PopIdAndSlotDateAndStartTimeLessThanAndEndTimeGreaterThanAndTimeSlotIdNot(
                popup.getPopId(), request.getSlotDate(), request.getEndTime(), request.getStartTime(), timeSlotId)) {
            throw new IllegalArgumentException("같은 날짜에 시간이 겹치는 회차가 이미 있습니다.");
        }
        slot.update(request); // 변경 감지 : save() 없이 트랜잭션이 끝날 때 자동으로 UPDATE (예약 인원은 그대로)
    }

    // 예약이 있거나 예약 이력이 있으면 삭제 불가
    @Transactional
    public void deleteTimeSlot(Long timeSlotId) {
        TimeSlot slot = findTimeSlotForUpdate(timeSlotId); // 락 : 삭제 중에 예약이 들어오지 않도록
        if (reservationRepo.existsByTimeSlot_TimeSlotId(timeSlotId)) { // 취소, 만료된 예약도 포함
            throw new IllegalArgumentException("예약 이력이 있는 회차는 삭제할 수 없습니다.");
        }
        try { // FK 제약조건으로 한 번 더 막음
            timeSlotRepo.delete(slot);
            timeSlotRepo.flush(); // 여기서 바로 DELETE 실행해야 FK 에러를 잡을 수 있음
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("예약 이력이 있는 회차는 삭제할 수 없습니다.");
        }
    }

    // ---------------- 내부 메소드 ----------------

    private PopupStore findPopup(Long popId) {
        if (popId == null) {
            throw new IllegalArgumentException("popId는 필수입니다.");
        }
        return popupRepo.findById(popId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 팝업입니다. popId=" + popId));
    }

    private TimeSlot findTimeSlot(Long timeSlotId) {
        return timeSlotRepo.findById(timeSlotId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회차입니다. timeSlotId=" + timeSlotId));
    }

    private TimeSlot findTimeSlotForUpdate(Long timeSlotId) {
        return timeSlotRepo.findByIdForUpdate(timeSlotId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회차입니다. timeSlotId=" + timeSlotId));
    }

    private List<TimeSlotResponse> toDtoList(List<TimeSlot> entityList) {
        List<TimeSlotResponse> dtoList = new ArrayList<TimeSlotResponse>();
        for (TimeSlot e : entityList) {
            dtoList.add(TimeSlotResponse.toDto(e));
        }
        return dtoList;
    }

    // 비어 있는 값은 팝업 정보 / 기본값으로 채움
    private void fillDefaults(PopupStore popup, TimeSlotGenerateRequest request) {
        if (request.getStartDate() == null) request.setStartDate(popup.getStartDate());
        if (request.getEndDate() == null) request.setEndDate(popup.getEndDate());
        if (request.getStartTime() == null) request.setStartTime(popup.getReservStart());
        if (request.getEndTime() == null) request.setEndTime(popup.getReservEnd());
        if (request.getIntervalMinutes() == null) request.setIntervalMinutes(60);
        if (request.getBreakMinutes() == null) request.setBreakMinutes(0);
        if (request.getCapacity() == null) request.setCapacity(popup.getReservPeopleCap());
    }

    private void checkGenerate(PopupStore popup, TimeSlotGenerateRequest request) {
        checkPopupPeriod(popup);
        checkTimeRange(request.getStartTime(), request.getEndTime(), "생성 시간");
        if ((request.getLunchStart() == null) != (request.getLunchEnd() == null)) {
            throw new IllegalArgumentException("점심시간은 lunchStart, lunchEnd를 같이 입력해주세요.");
        }
        if (request.getLunchStart() != null) {
            checkTimeRange(request.getLunchStart(), request.getLunchEnd(), "점심시간");
        }
        if (request.getIntervalMinutes() <= 0) {
            throw new IllegalArgumentException("회차 간격은 1분 이상이어야 합니다.");
        }
        if (request.getBreakMinutes() < 0) {
            throw new IllegalArgumentException("정비시간은 0분 이상이어야 합니다.");
        }
        if (request.getCapacity() == null || request.getCapacity() <= 0) {
            throw new IllegalArgumentException("회차 정원은 1명 이상이어야 합니다.");
        }
        if (request.getStartDate().isBefore(popup.getStartDate()) || request.getEndDate().isAfter(popup.getEndDate())
                || request.getStartDate().isAfter(request.getEndDate())) {
            throw new IllegalArgumentException("생성 기간은 팝업 운영기간 안이어야 합니다.");
        }
    }

    // 하루치 회차 목록 만들기 (저장은 generateTimeSlots에서)
    private List<TimeSlot> createDaySlots(PopupStore popup, LocalDate date, TimeSlotGenerateRequest request) {
        LocalTime time = LocalTime.parse(request.getStartTime());
        LocalTime rangeEnd = LocalTime.parse(request.getEndTime());
        LocalTime lunchStart = request.getLunchStart() != null ? LocalTime.parse(request.getLunchStart()) : null;
        LocalTime lunchEnd = request.getLunchEnd() != null ? LocalTime.parse(request.getLunchEnd()) : null;

        // 그날 이미 있는 회차 (시간 겹침 확인 + 회차 번호 계산, DB 조회는 하루 1번)
        List<TimeSlot> existingSlots = timeSlotRepo.findByPopupStore_PopIdAndSlotDateOrderByStartTimeAsc(popup.getPopId(), date);
        int maxSlotNum = 0;
        for (TimeSlot s : existingSlots) {
            if (s.getSlotNum() != null && s.getSlotNum() > maxSlotNum) {
                maxSlotNum = s.getSlotNum();
            }
        }

        int slotNum = maxSlotNum + 1; // 1, 2, 4가 있으면 5부터
        List<TimeSlot> daySlots = new ArrayList<TimeSlot>();

        while (true) {
            LocalTime end = time.plusMinutes(request.getIntervalMinutes());
            if (end.isAfter(rangeEnd) || !end.isAfter(time)) {
                break; // 종료 시간 초과 or 자정 넘김
            }
            if (lunchStart != null && time.isBefore(lunchEnd) && end.isAfter(lunchStart)) {
                time = lunchEnd; // 점심과 겹치면 점심 끝부터 다시
                continue;
            }
            if (!isOverlapped(existingSlots, time.toString(), end.toString())) { // 기존 회차와 겹치면 건너뜀
                daySlots.add(TimeSlot.builder()
                        .popupStore(popup)
                        .slotNum(slotNum++)
                        .slotDate(date)
                        .startTime(time.toString())
                        .endTime(end.toString())
                        .capacity(request.getCapacity())
                        .reservedCount(0)
                        .build());
            }
            LocalTime next = end.plusMinutes(request.getBreakMinutes()); // 다음 시작 = 끝 + 정비시간
            if (!next.isAfter(time)) {
                break; // 자정 넘김
            }
            time = next;
        }
        return daySlots;
    }

    // 기존 회차와 시간이 겹치는지 : 기존 시작 < 새 끝 그리고 기존 끝 > 새 시작
    private boolean isOverlapped(List<TimeSlot> slots, String start, String end) {
        for (TimeSlot s : slots) {
            if (s.getStartTime().compareTo(end) < 0 && s.getEndTime().compareTo(start) > 0) {
                return true;
            }
        }
        return false;
    }

    // 팝업 운영기간이 없으면 회차를 만들 수 없음 (팝업 필수값 검증 이전에 등록된 팝업 대비)
    private void checkPopupPeriod(PopupStore popup) {
        if (popup.getStartDate() == null || popup.getEndDate() == null) {
            throw new IllegalArgumentException("팝업 운영기간(시작일, 종료일)이 없어 회차를 만들 수 없습니다. 팝업 정보를 먼저 수정해주세요.");
        }
    }

    // 필수, HH:mm 형식, 시작 < 끝
    private void checkTimeRange(String start, String end, String name) {
        if (start == null || end == null) {
            throw new IllegalArgumentException(name + "의 시작과 끝을 입력해주세요.");
        }
        if (!start.matches(TIME_REGEX) || !end.matches(TIME_REGEX)) {
            throw new IllegalArgumentException(name + "은(는) HH:mm 형식이어야 합니다. 예) 10:00");
        }
        if (start.compareTo(end) >= 0) { // HH:mm 형식이라 문자열 비교로 순서 판단 가능
            throw new IllegalArgumentException(name + "의 끝은 시작보다 늦어야 합니다.");
        }
    }

    // 필수값, 시간, 정원, 팝업 운영기간 안인지
    private void checkTimeSlot(PopupStore popup, TimeSlotRequest request) {
        checkPopupPeriod(popup);
        if (request.getSlotDate() == null || request.getCapacity() == null) {
            throw new IllegalArgumentException("slotDate, capacity는 필수입니다.");
        }
        checkTimeRange(request.getStartTime(), request.getEndTime(), "회차 시간");
        if (request.getCapacity() <= 0) {
            throw new IllegalArgumentException("회차 정원은 1명 이상이어야 합니다.");
        }
        if (request.getSlotDate().isBefore(popup.getStartDate()) || request.getSlotDate().isAfter(popup.getEndDate())) {
            throw new IllegalArgumentException("회차 날짜는 팝업 운영기간(" + popup.getStartDate() + " ~ " + popup.getEndDate() + ") 안이어야 합니다.");
        }
    }
}
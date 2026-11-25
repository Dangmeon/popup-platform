package com.popq.platform.timeslot.service;

import com.popq.platform.popup.entity.PopupStore;
import com.popq.platform.popup.exception.PopupErrorCode;
import com.popq.platform.popup.exception.PopupException;
import com.popq.platform.popup.repository.PopupStoreRepository;
import com.popq.platform.popup.service.PopupAccessService;
import com.popq.platform.reservation.entity.Reservation;
import com.popq.platform.reservation.entity.ReservationStatus;
import com.popq.platform.reservation.repository.ReservationRepository;
import com.popq.platform.timeslot.dto.TimeSlotChangedEvent;
import com.popq.platform.timeslot.dto.TimeSlotGenerateRequest;
import com.popq.platform.timeslot.dto.TimeSlotRequest;
import com.popq.platform.timeslot.dto.TimeSlotResponse;
import com.popq.platform.timeslot.entity.TimeSlot;
import com.popq.platform.timeslot.exception.TimeSlotErrorCode;
import com.popq.platform.timeslot.exception.TimeSlotException;
import com.popq.platform.timeslot.repository.TimeSlotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true) // 기본 읽기 전용, 쓰기 메소드에만 @Transactional
public class TimeSlotService {

    private static final String TIME_REGEX = "^([01]\\d|2[0-3]):[0-5]\\d$"; // HH:mm
    private static final int MIN_INTERVAL_MINUTES = 30; // 자동 생성 회차 간격 최소값 (분)
    private LocalDateTime lastPauseCheckAt = LocalDateTime.now(); // 일시 중지 자동 취소 스케줄러가 마지막으로 확인한 시각 (서버 시작 시각부터 시작)

    TimeSlotRepository timeSlotRepo;
    PopupStoreRepository popupRepo;
    ReservationRepository reservationRepo;
    PopupAccessService accessService;
    ApplicationEventPublisher eventPublisher; // 회차 변경 이벤트 발행

    @Autowired
    public TimeSlotService(TimeSlotRepository timeSlotRepo, PopupStoreRepository popupRepo, ReservationRepository reservationRepo, PopupAccessService accessService, ApplicationEventPublisher eventPublisher) {
        this.timeSlotRepo = timeSlotRepo;
        this.popupRepo = popupRepo;
        this.reservationRepo = reservationRepo;
        this.accessService = accessService;
        this.eventPublisher = eventPublisher;
    }

    // 회차 목록 (date가 있으면 그 날짜만)
    public List<TimeSlotResponse> listTimeSlot(Long popId, LocalDate date) {
        findPopup(popId); // 없는 팝업이면 404
        List<TimeSlot> entityList;
        if (date != null) {
            entityList = timeSlotRepo.findByPopIdAndDate(popId, date);
        } else {
            entityList = timeSlotRepo.findByPopId(popId);
        }
        List<Long> reservedSlotIds = timeSlotRepo.findReservedSlotIdsByPopId(popId); // 예약 이력이 있는 회차 ID
        return toDtoList(entityList, reservedSlotIds);
    }

    public TimeSlotResponse detailTimeSlot(Long timeSlotId) {
        TimeSlot slot = findTimeSlot(timeSlotId); // 없는 회차면 404
        boolean hasHistory = reservationRepo.existsByTimeSlot_TimeSlotId(timeSlotId); // 취소, 만료된 예약도 포함
        return TimeSlotResponse.toDto(slot, hasHistory);
    }

    // 회차 1개 등록 : 등록된 회차 정보 반환
    @Transactional
    public TimeSlotResponse insertTimeSlot(Long popId, TimeSlotRequest request, String username) { // popId는 URL에서 받음
        PopupStore popup = findPopupForUpdate(popId); // 동시에 등록해도 겹치는 회차가 생기지 않도록
        accessService.checkAccess(popup, accessService.getLoginUser(username));

        popup.validateNotCancelled(); // 취소된 팝업에 회차 등록 불가

        checkTimeSlot(popup, request);
        if (timeSlotRepo.existsOverlap(
                popup.getPopId(), request.getSlotDate(), request.getStartTime(), request.getEndTime())) {
            throw new TimeSlotException(TimeSlotErrorCode.TIMESLOT_OVERLAP);
        }
        TimeSlot saved = timeSlotRepo.save(TimeSlot.toEntity(null, popup, request, 0));
        return TimeSlotResponse.toDto(saved, false); // 새로 만든 회차라 예약 이력 없음
    }

    // 자동 생성 : 생성된 개수 반환
    // 예) 10:00~16:00, 60분, 점심 12:30~13:30 → 10:00, 11:00, 13:30, 14:30
    @Transactional
    public int generateTimeSlots(Long popId, TimeSlotGenerateRequest request, String username) { // popId는 URL에서 받음
        PopupStore popup = findPopupForUpdate(popId); // 생성 버튼을 두 번 눌러도 회차가 중복 생성되지 않도록
        accessService.checkAccess(popup, accessService.getLoginUser(username)); // 담당 팝업이 아니면 403

        popup.validateNotCancelled(); // 취소된 팝업에 회차 등록 불가

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
    public TimeSlotResponse updateTimeSlot(Long timeSlotId, TimeSlotRequest request, String username) {
        TimeSlot slot = findTimeSlotForUpdate(timeSlotId); // 락 : 수정 중에 예약 인원이 바뀌지 않도록
        PopupStore popup = slot.getPopupStore();
        accessService.checkAccess(popup, accessService.getLoginUser(username)); // 담당 팝업이 아니면 403

        popup.validateNotCancelled(); // 취소된 팝업에 회차 수정 불가

        checkTimeSlot(popup, request);
        boolean hasHistory = reservationRepo.existsByTimeSlot_TimeSlotId(timeSlotId); // 아래 응답에서도 사용

        if (hasHistory) {
            boolean changed = !slot.getSlotDate().equals(request.getSlotDate())
                    || !slot.getStartTime().equals(request.getStartTime())
                    || !slot.getEndTime().equals(request.getEndTime());
            if (changed) {
                throw new TimeSlotException(TimeSlotErrorCode.TIMESLOT_TIME_LOCKED);
            }
        }
        if (request.getCapacity() < slot.getReservedCount()) {
            throw new TimeSlotException(TimeSlotErrorCode.CAPACITY_BELOW_RESERVED);
        }
        if (timeSlotRepo.existsOverlapExceptSelf(
                popup.getPopId(), request.getSlotDate(), request.getStartTime(), request.getEndTime(), timeSlotId)) {
            throw new TimeSlotException(TimeSlotErrorCode.TIMESLOT_OVERLAP);
        }
        slot.update(request); // 변경 감지 : save() 없이 트랜잭션이 끝날 때 자동으로 UPDATE (예약 인원은 그대로)
        return TimeSlotResponse.toDto(slot, hasHistory);
    }

    // 예약이 있거나 예약 이력이 있으면 삭제 불가
    @Transactional
    public void deleteTimeSlot(Long timeSlotId, String username) {
        TimeSlot slot = findTimeSlotForUpdate(timeSlotId); // 락 : 삭제 중에 예약이 들어오지 않도록
        PopupStore popup = slot.getPopupStore();
        accessService.checkAccess(slot.getPopupStore(), accessService.getLoginUser(username)); // 담당 팝업이 아니면 403

        popup.validateNotCancelled(); // 취소된 팝업에 회차 삭제 불가

        if (reservationRepo.existsByTimeSlot_TimeSlotId(timeSlotId)) { // 취소, 만료된 예약도 포함
            throw new TimeSlotException(TimeSlotErrorCode.TIMESLOT_HAS_RESERVATIONS);
        }
        try { // FK 제약조건으로 한 번 더 막음
            timeSlotRepo.delete(slot);
            timeSlotRepo.flush(); // 여기서 바로 DELETE 실행해야 FK 에러를 잡을 수 있음
        } catch (DataIntegrityViolationException e) {
            throw new TimeSlotException(TimeSlotErrorCode.TIMESLOT_HAS_RESERVATIONS);
        }
    }

    // ---------------- 내부 메소드 ----------------

    private PopupStore findPopup(Long popId) {
        return popupRepo.findById(popId)
                .orElseThrow(() -> new PopupException(PopupErrorCode.POPUP_NOT_FOUND));
    }

    // 팝업 조회 + 락 : 같은 팝업에 회차를 등록하는 요청은 한 번에 하나씩 처리됨
    private PopupStore findPopupForUpdate(Long popId) {
        return popupRepo.findByIdWithPessimisticLock(popId)
                .orElseThrow(() -> new PopupException(PopupErrorCode.POPUP_NOT_FOUND));
    }

    private TimeSlot findTimeSlot(Long timeSlotId) {
        return timeSlotRepo.findById(timeSlotId)
                .orElseThrow(() -> new TimeSlotException(TimeSlotErrorCode.TIMESLOT_NOT_FOUND));
    }

    private TimeSlot findTimeSlotForUpdate(Long timeSlotId) {
        return timeSlotRepo.findByIdForUpdate(timeSlotId)
                .orElseThrow(() -> new TimeSlotException(TimeSlotErrorCode.TIMESLOT_NOT_FOUND));
    }

    private List<TimeSlotResponse> toDtoList(List<TimeSlot> entityList, List<Long> reservedSlotIds) {
        List<TimeSlotResponse> dtoList = new ArrayList<TimeSlotResponse>();
        for (TimeSlot e : entityList) {
            boolean hasHistory = reservedSlotIds.contains(e.getTimeSlotId());
            dtoList.add(TimeSlotResponse.toDto(e, hasHistory));
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
        checkTimeRange(request.getStartTime(), request.getEndTime());
        if ((request.getLunchStart() == null) != (request.getLunchEnd() == null)) {
            throw new TimeSlotException(TimeSlotErrorCode.LUNCH_TIME_PAIR_REQUIRED);
        }
        if (request.getLunchStart() != null) {
            checkTimeRange(request.getLunchStart(), request.getLunchEnd());
        }
        if (request.getIntervalMinutes() < MIN_INTERVAL_MINUTES) {
            throw new TimeSlotException(TimeSlotErrorCode.INVALID_INTERVAL);
        }
        if (request.getBreakMinutes() < 0) {
            throw new TimeSlotException(TimeSlotErrorCode.INVALID_BREAK_MINUTES);
        }
        if (request.getCapacity() == null || request.getCapacity() <= 0) {
            throw new TimeSlotException(TimeSlotErrorCode.INVALID_CAPACITY);
        }
        if (request.getStartDate().isBefore(popup.getStartDate()) || request.getEndDate().isAfter(popup.getEndDate())
                || request.getStartDate().isAfter(request.getEndDate())) {
            throw new TimeSlotException(TimeSlotErrorCode.GENERATE_PERIOD_OUT_OF_RANGE);
        }
        checkReservTime(popup, request.getStartTime(), request.getEndTime()); // 생성 시간도 팝업 예약 시간 안
    }

    // 하루치 회차 목록 만들기 (저장은 generateTimeSlots에서)
    private List<TimeSlot> createDaySlots(PopupStore popup, LocalDate date, TimeSlotGenerateRequest request) {
        LocalTime time = LocalTime.parse(request.getStartTime());
        LocalTime rangeEnd = LocalTime.parse(request.getEndTime());
        LocalTime lunchStart = request.getLunchStart() != null ? LocalTime.parse(request.getLunchStart()) : null;
        LocalTime lunchEnd = request.getLunchEnd() != null ? LocalTime.parse(request.getLunchEnd()) : null;

        // 그날 이미 있는 회차 (시간 겹침 확인 + 회차 번호 계산, DB 조회는 하루 1번)
        List<TimeSlot> existingSlots = timeSlotRepo.findByPopIdAndDate(popup.getPopId(), date);
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
            throw new TimeSlotException(TimeSlotErrorCode.POPUP_PERIOD_MISSING);
        }
    }

    // 필수, HH:mm 형식, 시작 < 끝
    private void checkTimeRange(String start, String end) {
        if (start == null || end == null) {
            throw new TimeSlotException(TimeSlotErrorCode.TIME_REQUIRED);
        }
        if (!start.matches(TIME_REGEX) || !end.matches(TIME_REGEX)) {
            throw new TimeSlotException(TimeSlotErrorCode.INVALID_TIME_FORMAT);
        }
        if (start.compareTo(end) >= 0) { // HH:mm 형식이라 문자열 비교로 순서 판단 가능
            throw new TimeSlotException(TimeSlotErrorCode.INVALID_TIME_RANGE);
        }
    }

    // 필수값, 시간, 정원, 팝업 운영기간 안인지
    private void checkTimeSlot(PopupStore popup, TimeSlotRequest request) {
        checkPopupPeriod(popup);
        if (request.getSlotDate() == null || request.getCapacity() == null) {
            throw new TimeSlotException(TimeSlotErrorCode.TIMESLOT_REQUIRED_FIELD);
        }
        checkTimeRange(request.getStartTime(), request.getEndTime());
        if (request.getCapacity() <= 0) {
            throw new TimeSlotException(TimeSlotErrorCode.INVALID_CAPACITY);
        }
        if (request.getSlotDate().isBefore(popup.getStartDate()) || request.getSlotDate().isAfter(popup.getEndDate())) {
            throw new TimeSlotException(TimeSlotErrorCode.SLOT_DATE_OUT_OF_PERIOD);
        }
        checkReservTime(popup, request.getStartTime(), request.getEndTime());
    }

    // 팝업 예약 시간 안인지 : 예약 시작보다 일찍 시작하거나 예약 종료보다 늦게 끝나면 안 됨
    // 팝업에 예약 시간이 비어 있으면 제한 없음 / HH:mm 형식이라 문자열 비교로 판단
    private void checkReservTime(PopupStore popup, String start, String end) {
        if (popup.getReservStart() != null && start.compareTo(popup.getReservStart()) < 0) {
            throw new TimeSlotException(TimeSlotErrorCode.SLOT_TIME_OUT_OF_RESERV_TIME);
        }
        if (popup.getReservEnd() != null && end.compareTo(popup.getReservEnd()) > 0) {
            throw new TimeSlotException(TimeSlotErrorCode.SLOT_TIME_OUT_OF_RESERV_TIME);
        }
    }

    // 팀장 수정 ---------------------

    // 단일 회차 취소 공통 메소드 (예약 취소 + 인원 복구 + 회차 상태 변경)
    @Transactional
    public void cancelSlotInternal(TimeSlot slot) {
        if ("CANCELLED".equals(slot.getSlotStatus())) {
            throw new TimeSlotException(TimeSlotErrorCode.TIMESLOT_ALREADY_CANCELLED);
        }
        // 해당 회차의 활성화된 예약(HOLD, CONFIRMED) 조회 및 일괄 취소 + 인원 복구
        List<Reservation> activeReservations = reservationRepo.findActiveReservationsByTimeSlot(
                slot.getTimeSlotId(),
                List.of(ReservationStatus.HOLD, ReservationStatus.CONFIRMED)
        );
        for (Reservation reservation : activeReservations) {
            reservation.cancel();
            slot.decreaseReservedCount(reservation.getPeopleCount());
        }
        // 회차 상태 CANCELLED 변경 및 인원수 0으로 초기화
        slot.resetReservedCount();
        slot.cancel();
        eventPublisher.publishEvent(new TimeSlotChangedEvent(slot.getTimeSlotId())); // 예약 화면에 회차 취소를 바로 반영 : 트랜잭션 커밋 후 TimeSlotEventListener가 WebSocket 전송
    }

    // 관리자 개별 회차 취소 API용
    @Transactional
    public TimeSlotResponse cancelTimeSlot(Long timeSlotId, String username) {
        TimeSlot slot = findTimeSlotForUpdate(timeSlotId);
        PopupStore popup = slot.getPopupStore();
        accessService.checkAccess(popup, accessService.getLoginUser(username));

        // 이미 시작된 회차인지 검증
        LocalDate today = LocalDate.now();
        String nowTime = LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        if (slot.getSlotDate().isBefore(today)
                || (slot.getSlotDate().equals(today) && slot.getStartTime().compareTo(nowTime) <= 0)) {
            throw new TimeSlotException(TimeSlotErrorCode.TIMESLOT_ALREADY_STARTED); // 이미 시작된 회차는 캔슬 불가
        }

        cancelSlotInternal(slot);
        boolean hasHistory = reservationRepo.existsByTimeSlot_TimeSlotId(timeSlotId);
        return TimeSlotResponse.toDto(slot, hasHistory);
    }

    // 팝업 종료 시 시작 전 회차 일괄 취소용
    @Transactional
    public void cancelUpcomingSlotsByPopup(Long popId) {
        LocalDate today = LocalDate.now();
        String nowTime = LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        List<TimeSlot> upcomingSlots = timeSlotRepo.findUpcomingOpenSlots(popId, today, nowTime);
        for (TimeSlot slot : upcomingSlots) {
            TimeSlot lockedSlot = findTimeSlotForUpdate(slot.getTimeSlotId());
            cancelSlotInternal(lockedSlot);
        }
    }

    // 스케줄러 - 팝업이 PAUSED 상태일 때 시작 시간이 된 회차 자동 취소 (매 1분마다 실행)
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 * * * * *")
    @Transactional
    public void autoCancelPausedStartedSlots() {
        java.time.format.DateTimeFormatter hhmm = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        LocalDateTime from = lastPauseCheckAt;
        LocalDateTime to = LocalDateTime.now();
        List<TimeSlot> targetSlots = timeSlotRepo.findPausedSlotsStartedBetween(from.toLocalDate(), from.format(hhmm), to.toLocalDate(), to.format(hhmm));
        for (TimeSlot slot : targetSlots) {
            TimeSlot lockedSlot = findTimeSlotForUpdate(slot.getTimeSlotId());
            cancelSlotInternal(lockedSlot);
        }
        lastPauseCheckAt = to; // 다음 실행은 여기부터 확인 (중간에 오류가 나면 갱신하지 않아 다음에 다시 확인)
    }
}
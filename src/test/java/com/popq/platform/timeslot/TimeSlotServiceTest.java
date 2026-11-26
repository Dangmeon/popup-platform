package com.popq.platform.timeslot;

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
import com.popq.platform.timeslot.service.TimeSlotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// TimeSlotService 단위 테스트 : Repository, 권한 검사, 이벤트 발행은 Mock으로 만들어서 회차 규칙만 확인
@ExtendWith(MockitoExtension.class)
public class TimeSlotServiceTest {

    @Mock
    TimeSlotRepository timeSlotRepo;
    @Mock
    PopupStoreRepository popupRepo;
    @Mock
    ReservationRepository reservationRepo;
    @Mock
    PopupAccessService accessService;
    @Mock
    ApplicationEventPublisher eventPublisher;

    TimeSlotService timeSlotService;

    static final String ADMIN = "admin@gmail.com";
    static final LocalDate START = LocalDate.of(2026, 12, 1); // 팝업 운영 시작일
    static final LocalDate END = LocalDate.of(2026, 12, 10);  // 팝업 운영 종료일

    @BeforeEach
    void setUp() {
        timeSlotService = new TimeSlotService(timeSlotRepo, popupRepo, reservationRepo, accessService, eventPublisher);
    }

    // 운영 기간 12/1~12/10, 예약 시간 10:00~20:00 팝업
    private PopupStore createPopup(String opStatus) {
        return PopupStore.builder()
                .popId(1L)
                .title("테스트 팝업")
                .startDate(START)
                .endDate(END)
                .reservStart("10:00")
                .reservEnd("20:00")
                .reservPeopleCap(10)
                .opStatus(opStatus)
                .build();
    }

    // 회차 (날짜, 시간, 예약 인원, 상태만 바꿔서 사용)
    private TimeSlot createSlot(PopupStore popup, LocalDate date, String start, String end, int reservedCount, String slotStatus) {
        return TimeSlot.builder()
                .timeSlotId(10L)
                .popupStore(popup)
                .slotNum(1)
                .slotDate(date)
                .startTime(start)
                .endTime(end)
                .capacity(10)
                .reservedCount(reservedCount)
                .slotStatus(slotStatus)
                .build();
    }

    // 회차 등록·수정 요청
    private TimeSlotRequest createRequest(LocalDate date, String start, String end, int capacity) {
        TimeSlotRequest request = new TimeSlotRequest();
        request.setSlotDate(date);
        request.setStartTime(start);
        request.setEndTime(end);
        request.setCapacity(capacity);
        return request;
    }

    // 회차 예외가 나는지, 어떤 에러 코드인지 확인
    private void assertErrorCode(TimeSlotErrorCode expected, org.junit.jupiter.api.function.Executable action) {
        TimeSlotException e = assertThrows(TimeSlotException.class, action);
        assertEquals(expected, e.getErrorCode());
    }

    // 회차 등록

    @Test
    @DisplayName("등록 성공 : 예약 인원 0, 예약 이력 없음으로 저장")
    void insertTimeSlot_success() {
        when(popupRepo.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(createPopup("OPEN")));
        when(timeSlotRepo.save(any(TimeSlot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TimeSlotResponse response = timeSlotService.insertTimeSlot(1L, createRequest(START, "10:00", "11:00", 10), ADMIN);

        assertEquals("10:00", response.getStartTime());
        assertEquals(0, response.getReservedCount());
        assertEquals(10, response.getRemaining());
        assertFalse(response.isHasReservationHistory());
    }

    @Test
    @DisplayName("등록 실패 : 없는 팝업이면 POPUP_NOT_FOUND")
    void insertTimeSlot_popupNotFound() {
        when(popupRepo.findByIdWithPessimisticLock(99L)).thenReturn(Optional.empty());

        PopupException e = assertThrows(PopupException.class,
                () -> timeSlotService.insertTimeSlot(99L, createRequest(START, "10:00", "11:00", 10), ADMIN));
        assertEquals(PopupErrorCode.POPUP_NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("등록 실패 : 운영 취소된 팝업이면 POPUP_CANCELLED_MODIFICATION_DENIED")
    void insertTimeSlot_cancelledPopup() {
        when(popupRepo.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(createPopup("CANCELLED")));

        PopupException e = assertThrows(PopupException.class,
                () -> timeSlotService.insertTimeSlot(1L, createRequest(START, "10:00", "11:00", 10), ADMIN));
        assertEquals(PopupErrorCode.POPUP_CANCELLED_MODIFICATION_DENIED, e.getErrorCode());
        verify(timeSlotRepo, never()).save(any());
    }

    @Test
    @DisplayName("등록 실패 : 팝업 운영 기간 밖 날짜면 SLOT_DATE_OUT_OF_PERIOD")
    void insertTimeSlot_outOfPeriod() {
        when(popupRepo.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(createPopup("OPEN")));

        assertErrorCode(TimeSlotErrorCode.SLOT_DATE_OUT_OF_PERIOD,
                () -> timeSlotService.insertTimeSlot(1L, createRequest(END.plusDays(1), "10:00", "11:00", 10), ADMIN));
    }

    @Test
    @DisplayName("등록 실패 : 팝업 예약 시간(10:00~20:00) 밖이면 SLOT_TIME_OUT_OF_RESERV_TIME")
    void insertTimeSlot_outOfReservTime() {
        when(popupRepo.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(createPopup("OPEN")));

        assertErrorCode(TimeSlotErrorCode.SLOT_TIME_OUT_OF_RESERV_TIME,
                () -> timeSlotService.insertTimeSlot(1L, createRequest(START, "09:00", "10:00", 10), ADMIN));
    }

    @Test
    @DisplayName("등록 실패 : 종료 시간이 시작 시간보다 빠르면 INVALID_TIME_RANGE")
    void insertTimeSlot_invalidTimeRange() {
        when(popupRepo.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(createPopup("OPEN")));

        assertErrorCode(TimeSlotErrorCode.INVALID_TIME_RANGE,
                () -> timeSlotService.insertTimeSlot(1L, createRequest(START, "11:00", "10:00", 10), ADMIN));
    }

    @Test
    @DisplayName("등록 실패 : 정원이 0 이하면 INVALID_CAPACITY")
    void insertTimeSlot_invalidCapacity() {
        when(popupRepo.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(createPopup("OPEN")));

        assertErrorCode(TimeSlotErrorCode.INVALID_CAPACITY,
                () -> timeSlotService.insertTimeSlot(1L, createRequest(START, "10:00", "11:00", 0), ADMIN));
    }

    @Test
    @DisplayName("등록 실패 : 같은 날 다른 회차와 시간이 겹치면 TIMESLOT_OVERLAP")
    void insertTimeSlot_overlap() {
        when(popupRepo.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(createPopup("OPEN")));
        when(timeSlotRepo.existsOverlap(1L, START, "10:00", "11:00")).thenReturn(true);

        assertErrorCode(TimeSlotErrorCode.TIMESLOT_OVERLAP,
                () -> timeSlotService.insertTimeSlot(1L, createRequest(START, "10:00", "11:00", 10), ADMIN));
        verify(timeSlotRepo, never()).save(any());
    }

    @Test
    @DisplayName("등록 실패 : 담당이 아닌 매니저면 POPUP_ACCESS_DENIED, 저장 안 됨")
    void insertTimeSlot_accessDenied() {
        PopupStore popup = createPopup("OPEN");
        when(popupRepo.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(popup));
        doThrow(new PopupException(PopupErrorCode.POPUP_ACCESS_DENIED)).when(accessService).checkAccess(eq(popup), any());

        PopupException e = assertThrows(PopupException.class,
                () -> timeSlotService.insertTimeSlot(1L, createRequest(START, "10:00", "11:00", 10), "other@gmail.com"));
        assertEquals(PopupErrorCode.POPUP_ACCESS_DENIED, e.getErrorCode());
        verify(timeSlotRepo, never()).save(any());
    }

    // 회차 자동 생성

    // 하루(12/1)만 10:00~16:00, 60분 간격으로 생성하는 요청
    private TimeSlotGenerateRequest createGenerateRequest() {
        TimeSlotGenerateRequest request = new TimeSlotGenerateRequest();
        request.setStartDate(START);
        request.setEndDate(START);
        request.setStartTime("10:00");
        request.setEndTime("16:00");
        request.setIntervalMinutes(60);
        return request;
    }

    @Test
    @DisplayName("자동 생성 : 점심 12:30~13:30이면 10:00, 11:00, 13:30, 14:30 회차 4개 생성")
    @SuppressWarnings("unchecked")
    void generateTimeSlots_lunch() {
        when(popupRepo.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(createPopup("OPEN")));
        when(timeSlotRepo.findByPopIdAndDate(1L, START)).thenReturn(new ArrayList<>()); // 그날 기존 회차 없음
        TimeSlotGenerateRequest request = createGenerateRequest();
        request.setLunchStart("12:30");
        request.setLunchEnd("13:30");

        int count = timeSlotService.generateTimeSlots(1L, request, ADMIN);

        ArgumentCaptor<List<TimeSlot>> captor = ArgumentCaptor.forClass(List.class); // saveAll에 넘어간 목록 꺼내기
        verify(timeSlotRepo).saveAll(captor.capture());
        List<TimeSlot> saved = captor.getValue();
        assertEquals(4, count);
        assertEquals("10:00", saved.get(0).getStartTime());
        assertEquals("11:00", saved.get(1).getStartTime());
        assertEquals("13:30", saved.get(2).getStartTime());
        assertEquals("14:30", saved.get(3).getStartTime());
        assertEquals(10, saved.get(0).getCapacity()); // 정원을 비우면 팝업 reservPeopleCap
    }

    @Test
    @DisplayName("자동 생성 : 기존 회차와 겹치는 시간은 건너뛰고 회차 번호는 기존 다음부터")
    @SuppressWarnings("unchecked")
    void generateTimeSlots_skipExisting() {
        PopupStore popup = createPopup("OPEN");
        when(popupRepo.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(popup));
        List<TimeSlot> existing = new ArrayList<>();
        existing.add(createSlot(popup, START, "10:00", "11:00", 0, "OPEN")); // 1회차 10:00 이미 있음
        when(timeSlotRepo.findByPopIdAndDate(1L, START)).thenReturn(existing);
        TimeSlotGenerateRequest request = createGenerateRequest();
        request.setEndTime("13:00");

        int count = timeSlotService.generateTimeSlots(1L, request, ADMIN);

        ArgumentCaptor<List<TimeSlot>> captor = ArgumentCaptor.forClass(List.class);
        verify(timeSlotRepo).saveAll(captor.capture());
        List<TimeSlot> saved = captor.getValue();
        assertEquals(2, count); // 11:00, 12:00만 생성
        assertEquals("11:00", saved.get(0).getStartTime());
        assertEquals(2, saved.get(0).getSlotNum());
        assertEquals(3, saved.get(1).getSlotNum());
    }

    @Test
    @DisplayName("자동 생성 실패 : 간격이 30분 미만이면 INVALID_INTERVAL")
    void generateTimeSlots_invalidInterval() {
        when(popupRepo.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(createPopup("OPEN")));
        TimeSlotGenerateRequest request = createGenerateRequest();
        request.setIntervalMinutes(20);

        assertErrorCode(TimeSlotErrorCode.INVALID_INTERVAL, () -> timeSlotService.generateTimeSlots(1L, request, ADMIN));
        verify(timeSlotRepo, never()).saveAll(any());
    }

    @Test
    @DisplayName("자동 생성 실패 : 점심 시작만 있고 끝이 없으면 LUNCH_TIME_PAIR_REQUIRED")
    void generateTimeSlots_lunchPair() {
        when(popupRepo.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(createPopup("OPEN")));
        TimeSlotGenerateRequest request = createGenerateRequest();
        request.setLunchStart("12:00");

        assertErrorCode(TimeSlotErrorCode.LUNCH_TIME_PAIR_REQUIRED, () -> timeSlotService.generateTimeSlots(1L, request, ADMIN));
    }

    @Test
    @DisplayName("자동 생성 실패 : 생성 기간이 팝업 운영 기간 밖이면 GENERATE_PERIOD_OUT_OF_RANGE")
    void generateTimeSlots_periodOutOfRange() {
        when(popupRepo.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(createPopup("OPEN")));
        TimeSlotGenerateRequest request = createGenerateRequest();
        request.setEndDate(END.plusDays(1));

        assertErrorCode(TimeSlotErrorCode.GENERATE_PERIOD_OUT_OF_RANGE, () -> timeSlotService.generateTimeSlots(1L, request, ADMIN));
    }

    // 회차 수정

    @Test
    @DisplayName("수정 성공 : 예약 이력이 있어도 정원만 바꾸면 가능 + 실시간 회차 이벤트 발행")
    void updateTimeSlot_capacityOnly() {
        TimeSlot slot = createSlot(createPopup("OPEN"), START, "10:00", "11:00", 3, "OPEN");
        when(timeSlotRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(slot));
        when(reservationRepo.existsByTimeSlot_TimeSlotId(10L)).thenReturn(true);

        TimeSlotResponse response = timeSlotService.updateTimeSlot(10L, createRequest(START, "10:00", "11:00", 20), ADMIN);

        assertEquals(20, response.getCapacity());
        assertEquals(17, response.getRemaining()); // 20 - 예약 3명
        verify(eventPublisher).publishEvent(any(TimeSlotChangedEvent.class));
    }

    @Test
    @DisplayName("수정 실패 : 예약 이력이 있는데 시간을 바꾸면 TIMESLOT_TIME_LOCKED")
    void updateTimeSlot_timeLocked() {
        TimeSlot slot = createSlot(createPopup("OPEN"), START, "10:00", "11:00", 3, "OPEN");
        when(timeSlotRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(slot));
        when(reservationRepo.existsByTimeSlot_TimeSlotId(10L)).thenReturn(true);

        assertErrorCode(TimeSlotErrorCode.TIMESLOT_TIME_LOCKED,
                () -> timeSlotService.updateTimeSlot(10L, createRequest(START, "11:00", "12:00", 10), ADMIN));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("수정 실패 : 정원을 예약 인원보다 적게 바꾸면 CAPACITY_BELOW_RESERVED")
    void updateTimeSlot_capacityBelowReserved() {
        TimeSlot slot = createSlot(createPopup("OPEN"), START, "10:00", "11:00", 5, "OPEN");
        when(timeSlotRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(slot));

        assertErrorCode(TimeSlotErrorCode.CAPACITY_BELOW_RESERVED,
                () -> timeSlotService.updateTimeSlot(10L, createRequest(START, "10:00", "11:00", 4), ADMIN));
    }

    @Test
    @DisplayName("수정 실패 : 취소된 회차면 TIMESLOT_ALREADY_CANCELLED")
    void updateTimeSlot_cancelledSlot() {
        TimeSlot slot = createSlot(createPopup("OPEN"), START, "10:00", "11:00", 0, "CANCELLED");
        when(timeSlotRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(slot));

        assertErrorCode(TimeSlotErrorCode.TIMESLOT_ALREADY_CANCELLED,
                () -> timeSlotService.updateTimeSlot(10L, createRequest(START, "10:00", "11:00", 10), ADMIN));
    }

    @Test
    @DisplayName("수정 실패 : 없는 회차면 TIMESLOT_NOT_FOUND")
    void updateTimeSlot_notFound() {
        when(timeSlotRepo.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertErrorCode(TimeSlotErrorCode.TIMESLOT_NOT_FOUND,
                () -> timeSlotService.updateTimeSlot(99L, createRequest(START, "10:00", "11:00", 10), ADMIN));
    }

    //회차 삭제

    @Test
    @DisplayName("삭제 실패 : 예약 이력(취소·만료 포함)이 있으면 TIMESLOT_HAS_RESERVATIONS")
    void deleteTimeSlot_hasReservations() {
        TimeSlot slot = createSlot(createPopup("OPEN"), START, "10:00", "11:00", 0, "OPEN");
        when(timeSlotRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(slot));
        when(reservationRepo.existsByTimeSlot_TimeSlotId(10L)).thenReturn(true);

        assertErrorCode(TimeSlotErrorCode.TIMESLOT_HAS_RESERVATIONS, () -> timeSlotService.deleteTimeSlot(10L, ADMIN));
        verify(timeSlotRepo, never()).delete(any());
    }

    @Test
    @DisplayName("삭제 성공 : 예약 이력이 없으면 삭제 후 바로 flush")
    void deleteTimeSlot_success() {
        TimeSlot slot = createSlot(createPopup("OPEN"), START, "10:00", "11:00", 0, "OPEN");
        when(timeSlotRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(slot));

        timeSlotService.deleteTimeSlot(10L, ADMIN);

        verify(timeSlotRepo).delete(slot);
        verify(timeSlotRepo).flush();
    }

    // 회차 취소

    @Test
    @DisplayName("회차 취소 성공 : 진행 중 예약 취소, 예약 인원 0, 회차 CANCELLED, 실시간 이벤트 발행")
    void cancelTimeSlot_success() {
        TimeSlot slot = createSlot(createPopup("OPEN"), LocalDate.now().plusDays(1), "10:00", "11:00", 3, "OPEN"); // 내일 회차
        Reservation r1 = Reservation.create(null, slot, 1, LocalDateTime.now(), null);
        Reservation r2 = Reservation.create(null, slot, 2, LocalDateTime.now(), null);
        when(timeSlotRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(slot));
        when(reservationRepo.findActiveReservationsByTimeSlot(eq(10L), anyList())).thenReturn(List.of(r1, r2));
        when(reservationRepo.existsByTimeSlot_TimeSlotId(10L)).thenReturn(true);

        TimeSlotResponse response = timeSlotService.cancelTimeSlot(10L, ADMIN);

        assertEquals("CANCELLED", response.getSlotStatus());
        assertEquals(0, response.getReservedCount());
        assertTrue(response.isHasReservationHistory());
        assertEquals(ReservationStatus.CANCELLED, r1.getReservationStatus());
        assertEquals(ReservationStatus.CANCELLED, r2.getReservationStatus());
        verify(eventPublisher).publishEvent(any(TimeSlotChangedEvent.class));
    }

    @Test
    @DisplayName("회차 취소 실패 : 이미 시작한(지난) 회차면 TIMESLOT_ALREADY_STARTED")
    void cancelTimeSlot_alreadyStarted() {
        TimeSlot slot = createSlot(createPopup("OPEN"), LocalDate.now().minusDays(1), "10:00", "11:00", 0, "OPEN"); // 어제 회차
        when(timeSlotRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(slot));

        assertErrorCode(TimeSlotErrorCode.TIMESLOT_ALREADY_STARTED, () -> timeSlotService.cancelTimeSlot(10L, ADMIN));
        assertEquals("OPEN", slot.getSlotStatus());
    }

    @Test
    @DisplayName("회차 취소 실패 : 이미 취소된 회차면 TIMESLOT_ALREADY_CANCELLED")
    void cancelTimeSlot_alreadyCancelled() {
        TimeSlot slot = createSlot(createPopup("OPEN"), LocalDate.now().plusDays(1), "10:00", "11:00", 0, "CANCELLED");
        when(timeSlotRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(slot));

        assertErrorCode(TimeSlotErrorCode.TIMESLOT_ALREADY_CANCELLED, () -> timeSlotService.cancelTimeSlot(10L, ADMIN));
        verify(eventPublisher, never()).publishEvent(any());
    }

    // 회차 목록

    @Test
    @DisplayName("회차 목록 : 예약 이력이 있는 회차만 hasReservationHistory = true")
    void listTimeSlot_history() {
        PopupStore popup = createPopup("OPEN");
        TimeSlot slot1 = TimeSlot.builder().timeSlotId(10L).popupStore(popup).slotDate(START).startTime("10:00").endTime("11:00").capacity(10).reservedCount(0).build();
        TimeSlot slot2 = TimeSlot.builder().timeSlotId(11L).popupStore(popup).slotDate(START).startTime("11:00").endTime("12:00").capacity(10).reservedCount(0).build();
        when(popupRepo.findById(1L)).thenReturn(Optional.of(popup));
        when(timeSlotRepo.findByPopId(1L)).thenReturn(List.of(slot1, slot2));
        when(timeSlotRepo.findReservedSlotIdsByPopId(1L)).thenReturn(List.of(11L)); // 11번 회차만 예약 이력 있음

        List<TimeSlotResponse> result = timeSlotService.listTimeSlot(1L, null);

        assertFalse(result.get(0).isHasReservationHistory());
        assertTrue(result.get(1).isHasReservationHistory());
    }
}

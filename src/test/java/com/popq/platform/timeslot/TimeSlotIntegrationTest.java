package com.popq.platform.timeslot;

import com.popq.platform.auth.entity.Role;
import com.popq.platform.auth.entity.User;
import com.popq.platform.auth.repository.UserRepository;
import com.popq.platform.popup.entity.PopupStore;
import com.popq.platform.popup.repository.PopupStoreRepository;
import com.popq.platform.reservation.entity.Reservation;
import com.popq.platform.reservation.entity.ReservationStatus;
import com.popq.platform.reservation.repository.ReservationRepository;
import com.popq.platform.timeslot.dto.TimeSlotGenerateRequest;
import com.popq.platform.timeslot.dto.TimeSlotRequest;
import com.popq.platform.timeslot.dto.TimeSlotResponse;
import com.popq.platform.timeslot.entity.TimeSlot;
import com.popq.platform.timeslot.exception.TimeSlotErrorCode;
import com.popq.platform.timeslot.exception.TimeSlotException;
import com.popq.platform.timeslot.repository.TimeSlotRepository;
import com.popq.platform.timeslot.service.TimeSlotService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// 회차 통합 테스트 : 스프링을 실제로 띄우고 로컬 DB에 데이터를 넣어서 서비스 + 쿼리를 함께 확인
// @Transactional : 테스트가 끝나면 넣은 데이터를 모두 롤백 (개발 DB에 남지 않음)
// 실행 전 로컬 Oracle, Redis가 켜져 있어야 함
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class TimeSlotIntegrationTest {
    @Autowired
    TimeSlotService timeSlotService;
    @Autowired
    TimeSlotRepository timeSlotRepo;
    @Autowired
    PopupStoreRepository popupRepo;
    @Autowired
    ReservationRepository reservationRepo;
    @Autowired
    UserRepository userRepo;
    @Autowired
    EntityManager em;

    User admin;
    User member;
    PopupStore popup;
    LocalDate tomorrow = LocalDate.now().plusDays(1);

    @BeforeEach
    void setUp() {
        admin = saveUser(Role.ADMIN);
        member = saveUser(Role.USER);
        popup = savePopup();
    }

    // ------------------------- 테스트 데이터 만들기 -------------------------

    private User saveUser(Role role) {
        String random = UUID.randomUUID().toString().substring(0, 8);
        User user = User.builder()
                .username("it-" + random + "@gmail.com")
                .password("test")
                .name("통합테스트")
                .phone("010-" + random)
                .role(role)
                .build();
        return userRepo.save(user);
    }

    // 3일 전 ~ 5일 뒤 운영 중인 팝업 (예약 시간 09:00 ~ 18:00)
    private PopupStore savePopup() {
        PopupStore popup = PopupStore.builder()
                .title("IT" + UUID.randomUUID().toString().substring(0, 8))
                .location("성수")
                .category("ETC")
                .startDate(LocalDate.now().minusDays(3))
                .endDate(LocalDate.now().plusDays(5))
                .reservStart("09:00")
                .reservEnd("18:00")
                .reservPeopleCap(10)
                .build();
        return popupRepo.save(popup);
    }

    private TimeSlot saveSlot(PopupStore popup, LocalDate date, int slotNum, String start, String end, int reservedCount) {
        TimeSlot slot = TimeSlot.builder()
                .popupStore(popup)
                .slotNum(slotNum)
                .slotDate(date)
                .startTime(start)
                .endTime(end)
                .capacity(10)
                .reservedCount(reservedCount)
                .build();
        return timeSlotRepo.save(slot);
    }

    private Reservation saveReservation(TimeSlot slot, int peopleCount, ReservationStatus status) {
        Reservation reservation = Reservation.create(member, slot, peopleCount, LocalDateTime.now(), null);
        if (status == ReservationStatus.CONFIRMED) {
            reservation.confirm("qr-" + UUID.randomUUID());
        } else if (status == ReservationStatus.CANCELLED) {
            reservation.cancel();
        }
        return reservationRepo.save(reservation);
    }

    private TimeSlotRequest makeRequest(LocalDate date, String start, String end, int capacity) {
        TimeSlotRequest request = new TimeSlotRequest();
        request.setSlotDate(date);
        request.setStartTime(start);
        request.setEndTime(end);
        request.setCapacity(capacity);
        return request;
    }

    // 지금까지 바꾼 내용을 DB에 반영하고, 1차 캐시를 비워서 다음 조회는 DB에서 새로 읽게 함
    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    // ------------------------- 등록 -------------------------

    @Test
    @DisplayName("등록 : 시간이 겹치면 409, 끝 시간에 바로 이어지는 회차는 등록됨")
    void insertTimeSlot_overlapQuery() {
        saveSlot(popup, tomorrow, 1, "10:00", "11:00", 0);
        flushAndClear();

        TimeSlotException e = assertThrows(TimeSlotException.class,
                () -> timeSlotService.insertTimeSlot(popup.getPopId(), makeRequest(tomorrow, "10:30", "11:30", 10), admin.getUsername()));
        assertEquals(TimeSlotErrorCode.TIMESLOT_OVERLAP, e.getErrorCode());

        timeSlotService.insertTimeSlot(popup.getPopId(), makeRequest(tomorrow, "11:00", "12:00", 10), admin.getUsername());
        flushAndClear();

        assertEquals(2, timeSlotRepo.findByPopIdAndDate(popup.getPopId(), tomorrow).size());
    }

    // ------------------------- 자동 생성 -------------------------

    @Test
    @DisplayName("자동 생성 : 기존 회차·점심시간은 건너뛰고, 회차 번호는 기존 최대 번호 다음부터 저장")
    void generateTimeSlots_skipsExistingAndLunch() {
        saveSlot(popup, tomorrow, 1, "11:00", "12:00", 0); // 기존 회차
        flushAndClear();

        TimeSlotGenerateRequest request = new TimeSlotGenerateRequest();
        request.setStartDate(tomorrow);
        request.setEndDate(tomorrow);
        request.setStartTime("10:00");
        request.setEndTime("14:00");
        request.setIntervalMinutes(60);
        request.setLunchStart("12:00");
        request.setLunchEnd("13:00");

        int count = timeSlotService.generateTimeSlots(popup.getPopId(), request, admin.getUsername());
        flushAndClear();

        // 10:00 생성, 11:00 기존 회차라 건너뜀, 12:00 점심이라 건너뜀, 13:00 생성
        assertEquals(2, count);
        List<TimeSlot> slots = timeSlotRepo.findByPopIdAndDate(popup.getPopId(), tomorrow); // 시작시간 순
        assertEquals(3, slots.size());
        assertEquals("10:00", slots.get(0).getStartTime());
        assertEquals(2, slots.get(0).getSlotNum());
        assertEquals("11:00", slots.get(1).getStartTime());
        assertEquals(1, slots.get(1).getSlotNum());
        assertEquals("13:00", slots.get(2).getStartTime());
        assertEquals(3, slots.get(2).getSlotNum());
        assertEquals(10, slots.get(2).getCapacity()); // 정원을 비우면 팝업의 reservPeopleCap
    }

    // ------------------------- 수정 -------------------------

    @Test
    @DisplayName("수정 : 취소된 예약만 있어도 시간 변경은 409, 정원 변경은 DB에 저장")
    void updateTimeSlot_withCancelledHistory() {
        TimeSlot slot = saveSlot(popup, tomorrow, 1, "10:00", "11:00", 0);
        saveReservation(slot, 2, ReservationStatus.CANCELLED);
        flushAndClear();

        TimeSlotException e = assertThrows(TimeSlotException.class,
                () -> timeSlotService.updateTimeSlot(slot.getTimeSlotId(), makeRequest(tomorrow, "11:00", "12:00", 10), admin.getUsername()));
        assertEquals(TimeSlotErrorCode.TIMESLOT_TIME_LOCKED, e.getErrorCode());

        timeSlotService.updateTimeSlot(slot.getTimeSlotId(), makeRequest(tomorrow, "10:00", "11:00", 20), admin.getUsername());
        flushAndClear();

        TimeSlot saved = timeSlotRepo.findById(slot.getTimeSlotId()).get();
        assertEquals(20, saved.getCapacity());
        assertEquals("10:00", saved.getStartTime());
    }

    // ------------------------- 삭제 -------------------------

    @Test
    @DisplayName("삭제 : 예약 이력이 없으면 DB에서 삭제, 취소된 예약만 있어도 409로 막힘")
    void deleteTimeSlot_checksHistory() {
        TimeSlot emptySlot = saveSlot(popup, tomorrow, 1, "10:00", "11:00", 0);
        TimeSlot historySlot = saveSlot(popup, tomorrow, 2, "11:00", "12:00", 0);
        saveReservation(historySlot, 2, ReservationStatus.CANCELLED);
        flushAndClear();

        timeSlotService.deleteTimeSlot(emptySlot.getTimeSlotId(), admin.getUsername());

        TimeSlotException e = assertThrows(TimeSlotException.class,
                () -> timeSlotService.deleteTimeSlot(historySlot.getTimeSlotId(), admin.getUsername()));
        assertEquals(TimeSlotErrorCode.TIMESLOT_HAS_RESERVATIONS, e.getErrorCode());
        flushAndClear();

        assertTrue(timeSlotRepo.findById(emptySlot.getTimeSlotId()).isEmpty());
        assertTrue(timeSlotRepo.findById(historySlot.getTimeSlotId()).isPresent());
    }

    // ------------------------- 회차 취소 -------------------------

    @Test
    @DisplayName("회차 취소 : 회차는 CANCELLED·예약 인원 0, 확정 예약은 CANCELLED로 DB에 저장")
    void cancelTimeSlot_savesCancelled() {
        TimeSlot slot = saveSlot(popup, tomorrow, 1, "10:00", "11:00", 3);
        Reservation r1 = saveReservation(slot, 1, ReservationStatus.CONFIRMED);
        member = saveUser(Role.USER); // 같은 회원은 한 회차에 활성 예약 1개만 가능 (UQ_RES_MEMBER_ACTIVE) → 두 번째 예약은 다른 회원으로
        Reservation r2 = saveReservation(slot, 2, ReservationStatus.CONFIRMED);
        flushAndClear();
        timeSlotService.cancelTimeSlot(slot.getTimeSlotId(), admin.getUsername());
        flushAndClear();

        TimeSlot saved = timeSlotRepo.findById(slot.getTimeSlotId()).get();
        assertEquals("CANCELLED", saved.getSlotStatus());
        assertEquals(0, saved.getReservedCount());
        assertEquals(ReservationStatus.CANCELLED, reservationRepo.findById(r1.getReservationId()).get().getReservationStatus());
        assertEquals(ReservationStatus.CANCELLED, reservationRepo.findById(r2.getReservationId()).get().getReservationStatus());
    }

    // ------------------------- 목록 -------------------------

    @Test
    @DisplayName("목록 : 예약 이력이 있는 회차만 hasReservationHistory = true")
    void listTimeSlot_hasHistoryFlag() {
        TimeSlot emptySlot = saveSlot(popup, tomorrow, 1, "10:00", "11:00", 0);
        TimeSlot historySlot = saveSlot(popup, tomorrow, 2, "11:00", "12:00", 0);
        saveReservation(historySlot, 2, ReservationStatus.CANCELLED);
        flushAndClear();

        List<TimeSlotResponse> result = timeSlotService.listTimeSlot(popup.getPopId(), tomorrow);

        assertEquals(2, result.size());
        assertEquals(emptySlot.getTimeSlotId(), result.get(0).getTimeSlotId());
        assertFalse(result.get(0).isHasReservationHistory());
        assertEquals(historySlot.getTimeSlotId(), result.get(1).getTimeSlotId());
        assertTrue(result.get(1).isHasReservationHistory());
    }

    // ------------------------- 일시 중지 스케줄러 쿼리 -------------------------

    @Test
    @DisplayName("스케줄러 쿼리 : PAUSED 팝업의 OPEN 회차 중 확인 구간 안에 시작한 회차만 조회")
    void findPausedSlotsStartedBetween_query() {
        PopupStore pausedPopup = savePopup();
        pausedPopup.pause();
        LocalDate today = LocalDate.now();
        TimeSlot target = saveSlot(pausedPopup, today, 1, "10:00", "11:00", 0);         // 대상
        TimeSlot outside = saveSlot(pausedPopup, today, 2, "15:00", "16:00", 0);        // 구간 밖
        TimeSlot cancelled = saveSlot(pausedPopup, today, 3, "10:30", "11:30", 0);
        cancelled.cancel();                                                              // 이미 취소된 회차
        TimeSlot openPopupSlot = saveSlot(popup, today, 1, "10:00", "11:00", 0);        // OPEN 팝업 회차
        flushAndClear();

        // 오늘 09:00 이후 ~ 오늘 12:00까지 시작한 회차
        List<TimeSlot> result = timeSlotRepo.findPausedSlotsStartedBetween(today, "09:00", today, "12:00");

        List<Long> ids = new ArrayList<Long>();
        for (TimeSlot slot : result) {
            ids.add(slot.getTimeSlotId());
        }
        assertTrue(ids.contains(target.getTimeSlotId()));
        assertFalse(ids.contains(outside.getTimeSlotId()));
        assertFalse(ids.contains(cancelled.getTimeSlotId()));
        assertFalse(ids.contains(openPopupSlot.getTimeSlotId()));
    }
}

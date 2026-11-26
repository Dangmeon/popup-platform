package com.popq.platform.popup;

import com.popq.platform.auth.entity.Role;
import com.popq.platform.auth.entity.User;
import com.popq.platform.auth.repository.UserRepository;
import com.popq.platform.favorite.entity.Favorite;
import com.popq.platform.favorite.repository.FavoriteRepository;
import com.popq.platform.popup.dto.PopupStoreResponse;
import com.popq.platform.popup.entity.PopupStore;
import com.popq.platform.popup.exception.PopupErrorCode;
import com.popq.platform.popup.exception.PopupException;
import com.popq.platform.popup.repository.PopupStoreRepository;
import com.popq.platform.popup.service.PopupStoreService;
import com.popq.platform.reservation.entity.Reservation;
import com.popq.platform.reservation.entity.ReservationStatus;
import com.popq.platform.reservation.repository.ReservationRepository;
import com.popq.platform.timeslot.entity.TimeSlot;
import com.popq.platform.timeslot.repository.TimeSlotRepository;
import com.popq.platform.waiting.entity.WaitingQueue;
import com.popq.platform.waiting.entity.WaitingStatus;
import com.popq.platform.waiting.repository.WaitingQueueRepository;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// 팝업 통합 테스트 : 스프링을 실제로 띄우고 로컬 DB에 데이터를 넣어서 서비스 + 쿼리를 함께 확인
// @Transactional : 테스트가 끝나면 넣은 데이터를 모두 롤백 (개발 DB에 남지 않음)
// 실행 전 로컬 Oracle, Redis가 켜져 있어야 함
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class PopupIntegrationTest {

    @Autowired
    PopupStoreService popupService;
    @Autowired
    PopupStoreRepository popupRepo;
    @Autowired
    TimeSlotRepository timeSlotRepo;
    @Autowired
    ReservationRepository reservationRepo;
    @Autowired
    WaitingQueueRepository waitingRepo;
    @Autowired
    FavoriteRepository favoriteRepo;
    @Autowired
    UserRepository userRepo;
    @Autowired
    EntityManager em;

    User admin;
    User manager;
    User member;
    String tag; // 이번 테스트에서 만든 팝업만 찾기 위한 고유 문자열 (기존 데이터와 섞이지 않게)

    @BeforeEach
    void setUp() {
        tag = "IT" + UUID.randomUUID().toString().substring(0, 8);
        admin = saveUser(Role.ADMIN);
        manager = saveUser(Role.MANAGER);
        member = saveUser(Role.USER);
    }

    // 테스트 데이터 만들기

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

    // 3일 전 ~ 5일 뒤 운영 중인 팝업 (담당 매니저 지정)
    private PopupStore savePopup(String title) {
        PopupStore popup = PopupStore.builder()
                .title(title)
                .location("성수")
                .category("ETC")
                .startDate(LocalDate.now().minusDays(3))
                .endDate(LocalDate.now().plusDays(5))
                .reservStart("00:00")
                .reservEnd("23:59")
                .reservPeopleCap(10)
                .build();
        popup.assignManager(manager);
        return popupRepo.save(popup);
    }

    private TimeSlot saveSlot(PopupStore popup, LocalDate date, int reservedCount) {
        TimeSlot slot = TimeSlot.builder()
                .popupStore(popup)
                .slotNum(1)
                .slotDate(date)
                .startTime("10:00")
                .endTime("11:00")
                .capacity(10)
                .reservedCount(reservedCount)
                .build();
        return timeSlotRepo.save(slot);
    }

    // 확정(CONFIRMED) 예약
    private Reservation saveConfirmedReservation(TimeSlot slot, int peopleCount) {
        Reservation reservation = Reservation.create(member, slot, peopleCount, LocalDateTime.now(), null);
        reservation.confirm("qr-" + UUID.randomUUID());
        return reservationRepo.save(reservation);
    }

    private WaitingQueue saveWaiting(PopupStore popup, WaitingStatus status) {
        WaitingQueue waiting = WaitingQueue.builder()
                .popupStore(popup)
                .phone("010-0000-0000")
                .peopleCount(2)
                .queueNumber(1)
                .build(); // 처음에는 WAITING
        if (status == WaitingStatus.CANCELLED) {
            waiting.cancel();
        } else if (status == WaitingStatus.ENTERED) {
            waiting.call(10);
            waiting.enter();
        }
        return waitingRepo.save(waiting);
    }

    // 지금까지 바꾼 내용을 DB에 반영하고, 1차 캐시를 비워서 다음 조회는 DB에서 새로 읽게 함
    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    // 운영 취소

    @Test
    @DisplayName("운영 취소 : 시작 전 회차·예약은 CANCELLED로 DB에 저장되고, 지난 회차·예약은 그대로")
    void cancelPopup_savesCancelledSlotsAndReservations() {
        PopupStore popup = savePopup(tag);
        TimeSlot pastSlot = saveSlot(popup, LocalDate.now().minusDays(1), 2);   // 어제 회차
        TimeSlot futureSlot = saveSlot(popup, LocalDate.now().plusDays(1), 2);  // 내일 회차
        Reservation pastReservation = saveConfirmedReservation(pastSlot, 2);
        Reservation futureReservation = saveConfirmedReservation(futureSlot, 2);
        flushAndClear();

        popupService.cancelPopup(popup.getPopId(), admin.getUsername());
        flushAndClear();

        assertEquals("CANCELLED", popupRepo.findById(popup.getPopId()).get().getOpStatus());

        TimeSlot savedFuture = timeSlotRepo.findById(futureSlot.getTimeSlotId()).get();
        assertEquals("CANCELLED", savedFuture.getSlotStatus());
        assertEquals(0, savedFuture.getReservedCount());
        assertEquals(ReservationStatus.CANCELLED,
                reservationRepo.findById(futureReservation.getReservationId()).get().getReservationStatus());

        TimeSlot savedPast = timeSlotRepo.findById(pastSlot.getTimeSlotId()).get();
        assertEquals("OPEN", savedPast.getSlotStatus()); // 이미 지난 회차는 취소하지 않음
        assertEquals(2, savedPast.getReservedCount());
        assertEquals(ReservationStatus.CONFIRMED,
                reservationRepo.findById(pastReservation.getReservationId()).get().getReservationStatus());
    }

    @Test
    @DisplayName("운영 취소 : 대기 중 웨이팅은 CANCELLED, 입장 완료 웨이팅은 그대로")
    void cancelPopup_cancelsActiveWaitings() {
        PopupStore popup = savePopup(tag);
        WaitingQueue waiting = saveWaiting(popup, WaitingStatus.WAITING);
        WaitingQueue entered = saveWaiting(popup, WaitingStatus.ENTERED);
        flushAndClear();

        popupService.cancelPopup(popup.getPopId(), admin.getUsername());
        flushAndClear();

        assertEquals(WaitingStatus.CANCELLED, waitingRepo.findById(waiting.getWaitingId()).get().getWaitingStatus());
        assertEquals(WaitingStatus.ENTERED, waitingRepo.findById(entered.getWaitingId()).get().getWaitingStatus());
    }

    // 팝업 삭제

    @Test
    @DisplayName("삭제 : 입장 기록이 없으면 팝업과 함께 웨이팅·찜도 DB에서 삭제")
    void deletePopup_deletesWaitingsAndFavorites() {
        PopupStore popup = savePopup(tag);
        WaitingQueue waiting = saveWaiting(popup, WaitingStatus.WAITING);
        WaitingQueue cancelled = saveWaiting(popup, WaitingStatus.CANCELLED);
        Favorite favorite = favoriteRepo.save(Favorite.createPopupFavorite(member, popup));
        flushAndClear();

        popupService.deletePopup(popup.getPopId(), admin.getUsername());
        flushAndClear();

        assertTrue(popupRepo.findById(popup.getPopId()).isEmpty());
        assertTrue(waitingRepo.findById(waiting.getWaitingId()).isEmpty());
        assertTrue(waitingRepo.findById(cancelled.getWaitingId()).isEmpty());
        assertTrue(favoriteRepo.findById(favorite.getFavoriteId()).isEmpty());
    }

    @Test
    @DisplayName("삭제 실패 : 입장 완료 웨이팅이 있으면 409, 팝업·웨이팅 모두 남아 있음")
    void deletePopup_blockedByEnteredWaiting() {
        PopupStore popup = savePopup(tag);
        WaitingQueue entered = saveWaiting(popup, WaitingStatus.ENTERED);
        WaitingQueue waiting = saveWaiting(popup, WaitingStatus.WAITING);
        flushAndClear();

        PopupException e = assertThrows(PopupException.class,
                () -> popupService.deletePopup(popup.getPopId(), admin.getUsername()));
        assertEquals(PopupErrorCode.POPUP_HAS_ENTERED_WAITINGS, e.getErrorCode());
        flushAndClear();

        assertTrue(popupRepo.findById(popup.getPopId()).isPresent());
        assertTrue(waitingRepo.findById(entered.getWaitingId()).isPresent());
        assertTrue(waitingRepo.findById(waiting.getWaitingId()).isPresent());
    }

    // 목록 검색

    @Test
    @DisplayName("사용자 목록 : 검색어로 찾을 수 있고, 운영 취소된 팝업은 목록에서 빠짐")
    void listUserPopups_excludesCancelled() {
        PopupStore open = savePopup(tag + " 오픈");
        PopupStore cancelled = savePopup(tag + " 취소");
        popupService.cancelPopup(cancelled.getPopId(), admin.getUsername());
        flushAndClear();

        List<PopupStoreResponse> result = popupService.listUserPopups(null, tag, null, null, null);

        assertEquals(1, result.size());
        assertEquals(open.getPopId(), result.get(0).getPopId());
    }

    // 매니저 권한

    @Test
    @DisplayName("일시 중지 : 담당 매니저는 PAUSED로 저장되고, 다른 매니저는 403")
    void pausePopup_managerAccess() {
        PopupStore popup = savePopup(tag);
        User otherManager = saveUser(Role.MANAGER);
        flushAndClear();

        PopupException e = assertThrows(PopupException.class,
                () -> popupService.pausePopup(popup.getPopId(), otherManager.getUsername()));
        assertEquals(PopupErrorCode.POPUP_ACCESS_DENIED, e.getErrorCode());

        popupService.pausePopup(popup.getPopId(), manager.getUsername());
        flushAndClear();

        assertEquals("PAUSED", popupRepo.findById(popup.getPopId()).get().getOpStatus());
    }
}

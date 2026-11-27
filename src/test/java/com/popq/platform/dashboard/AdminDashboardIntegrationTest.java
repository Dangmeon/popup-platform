package com.popq.platform.dashboard;

import com.popq.platform.auth.entity.Role;
import com.popq.platform.auth.entity.User;
import com.popq.platform.auth.repository.UserRepository;
import com.popq.platform.dashboard.dto.AdminDashboardResponse;
import com.popq.platform.dashboard.dto.DailyReservationResponse;
import com.popq.platform.dashboard.dto.PopupRankingResponse;
import com.popq.platform.dashboard.service.AdminDashboardService;
import com.popq.platform.order.entity.Order;
import com.popq.platform.order.entity.OrderItem;
import com.popq.platform.order.repository.OrderRepository;
import com.popq.platform.popup.entity.PopupStore;
import com.popq.platform.popup.repository.PopupStoreRepository;
import com.popq.platform.product.entity.Product;
import com.popq.platform.product.repository.ProductRepository;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

// 관리자 대시보드 통합 테스트 : 스프링을 실제로 띄우고 로컬 DB에 데이터를 넣어서 집계 쿼리 결과를 확인
// @Transactional : 테스트가 끝나면 넣은 데이터를 모두 롤백 (개발 DB에 남지 않음)
// 매니저는 매번 새로 만들어서 기존 데이터와 섞이지 않음 -> 매니저 기준 숫자는 정확히 비교
// ADMIN은 전체 데이터라 기존 데이터가 섞이므로, 넣기 전과 넣은 후의 차이로 비교
// 실행 전 로컬 Oracle, Redis가 켜져 있어야 함
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AdminDashboardIntegrationTest {

    @Autowired
    AdminDashboardService dashboardService;
    @Autowired
    PopupStoreRepository popupRepo;
    @Autowired
    TimeSlotRepository timeSlotRepo;
    @Autowired
    ReservationRepository reservationRepo;
    @Autowired
    WaitingQueueRepository waitingRepo;
    @Autowired
    ProductRepository productRepo;
    @Autowired
    OrderRepository orderRepo;
    @Autowired
    UserRepository userRepo;
    @Autowired
    EntityManager em;

    User admin;
    User manager;
    User otherManager;
    LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        admin = saveUser(Role.ADMIN);
        manager = saveUser(Role.MANAGER);
        otherManager = saveUser(Role.MANAGER);
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

    // 시작일을 다르게 줄 수 있는 팝업 (랭킹 동점 정렬이 시작일 최신순이라 시작일이 겹치지 않게)
    private PopupStore savePopup(User manager, int startDaysAgo) {
        PopupStore popup = PopupStore.builder()
                .title("IT" + UUID.randomUUID().toString().substring(0, 8))
                .location("성수")
                .category("ETC")
                .startDate(today.minusDays(startDaysAgo))
                .endDate(today.plusDays(5))
                .reservStart("09:00")
                .reservEnd("20:00")
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

    // 예약마다 새 회원 사용 (같은 회원은 한 회차에 활성 예약 1개만 가능 : UQ_RES_MEMBER_ACTIVE)
    private Reservation saveReservation(TimeSlot slot, int peopleCount, ReservationStatus status, LocalDateTime reservedAt) {
        Reservation reservation = Reservation.create(saveUser(Role.USER), slot, peopleCount, reservedAt, null);
        if (status == ReservationStatus.CONFIRMED) {
            reservation.confirm("qr-" + UUID.randomUUID());
        } else if (status == ReservationStatus.USED) {
            reservation.confirm("qr-" + UUID.randomUUID());
            ReflectionTestUtils.setField(reservation, "reservationStatus", ReservationStatus.USED); // 입장 처리 메서드가 엔티티에 없어서 직접 넣음
        } else if (status == ReservationStatus.CANCELLED) {
            reservation.cancel();
        }
        return reservationRepo.save(reservation);
    }

    private Reservation saveReservation(TimeSlot slot, int peopleCount, ReservationStatus status) {
        return saveReservation(slot, peopleCount, status, LocalDateTime.now());
    }

    private WaitingQueue saveWaiting(PopupStore popup, WaitingStatus status) {
        WaitingQueue waiting = WaitingQueue.builder()
                .popupStore(popup)
                .phone("010-" + UUID.randomUUID().toString().substring(0, 8))
                .peopleCount(2)
                .queueNumber(1)
                .build(); // 처음에는 WAITING, 접수 시각은 지금
        if (status == WaitingStatus.CALLED) {
            waiting.call(10);
        } else if (status == WaitingStatus.ENTERED) {
            waiting.call(10);
            waiting.enter();
        }
        return waitingRepo.save(waiting);
    }

    // 판매 중인 상품 (판매 종료 7일 뒤)
    private Product saveProduct(PopupStore popup, String name, int stock) {
        Product product = Product.builder()
                .popupStore(popup)
                .productName(name)
                .price(10000)
                .stock(stock)
                .saleStart(LocalDateTime.now().minusDays(1))
                .saleEnd(LocalDateTime.now().plusDays(7))
                .maxQty(1)
                .build();
        return productRepo.save(product);
    }

    // 주문 상품 여러 개를 넣은 주문 (paid = true면 결제 완료)
    private Order saveOrder(boolean paid, Product... products) {
        int total = 0;
        for (Product product : products) {
            total += product.getPrice();
        }
        Order order = Order.createOrder(saveUser(Role.USER), total, LocalDateTime.now().plusMinutes(10),
                "통합테스트", "010-0000-0000", "서울", "101호", null, null);
        for (Product product : products) {
            order.addOrderItem(OrderItem.createOrderItem(product, 1, product.getPrice(), product.getPrice()));
        }
        if (paid) {
            order.completePayment();
        }
        return orderRepo.save(order); // 주문 상품도 같이 저장 (cascade)
    }

    // 지금까지 바꾼 내용을 DB에 반영하고, 1차 캐시를 비워서 다음 조회는 DB에서 새로 읽게 함
    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    // ------------------------- 예약 많은 순 팝업 목록 -------------------------

    @Test
    @DisplayName("랭킹 : 매니저는 본인 팝업만, CONFIRMED·USED 인원 합계 순, 취소 예약은 빠지고 예약 없는 팝업은 0")
    void ranking_managerOwnPopupsSortedByPeople() {
        PopupStore popupA = savePopup(manager, 1);
        PopupStore popupB = savePopup(manager, 2);
        PopupStore popupC = savePopup(manager, 3); // 예약 없음
        PopupStore otherPopup = savePopup(otherManager, 1);

        TimeSlot slotA = saveSlot(popupA, today.plusDays(1), 0);
        saveReservation(slotA, 3, ReservationStatus.CONFIRMED);
        saveReservation(slotA, 5, ReservationStatus.CANCELLED); // 집계에서 빠짐

        TimeSlot slotB = saveSlot(popupB, today, 0);
        saveReservation(slotB, 1, ReservationStatus.CONFIRMED);
        saveReservation(slotB, 3, ReservationStatus.USED);

        TimeSlot otherSlot = saveSlot(otherPopup, today, 0);
        saveReservation(otherSlot, 9, ReservationStatus.CONFIRMED); // 다른 매니저 팝업
        flushAndClear();

        List<PopupRankingResponse> result = dashboardService.listReservationRanking(manager.getUsername(), null, null);

        assertEquals(3, result.size());
        assertEquals(popupB.getPopId(), result.get(0).getPopId());
        assertEquals(1, result.get(0).getRank());
        assertEquals(4, result.get(0).getReservedPeople());
        assertEquals(2, result.get(0).getReservationCount());
        assertEquals(popupA.getPopId(), result.get(1).getPopId());
        assertEquals(3, result.get(1).getReservedPeople());
        assertEquals(1, result.get(1).getReservationCount());
        assertEquals(popupC.getPopId(), result.get(2).getPopId());
        assertEquals(0, result.get(2).getReservedPeople());
    }

    @Test
    @DisplayName("랭킹 : days를 주면 그 기간 안에 들어온 예약만 집계")
    void ranking_daysFilter() {
        PopupStore popup = savePopup(manager, 1);
        TimeSlot slot = saveSlot(popup, today.plusDays(1), 0);
        saveReservation(slot, 2, ReservationStatus.CONFIRMED);                                   // 오늘 예약
        saveReservation(slot, 4, ReservationStatus.CONFIRMED, LocalDateTime.now().minusDays(10)); // 10일 전 예약
        flushAndClear();

        List<PopupRankingResponse> all = dashboardService.listReservationRanking(manager.getUsername(), null, null);
        List<PopupRankingResponse> week = dashboardService.listReservationRanking(manager.getUsername(), 7, null);

        assertEquals(6, all.get(0).getReservedPeople());
        assertEquals(2, week.get(0).getReservedPeople());
    }

    // ------------------------- 대시보드 (매니저) -------------------------

    @Test
    @DisplayName("대시보드 : 오늘 예약 수, 현재 웨이팅 수, 7일 추이, 혼잡도가 본인 팝업 기준으로 집계")
    void dashboard_manager_reservationAndWaiting() {
        PopupStore popup = savePopup(manager, 1);
        TimeSlot todaySlot = saveSlot(popup, today, 5); // 정원 10, 예약 인원 5 -> 혼잡도 50%
        saveReservation(todaySlot, 2, ReservationStatus.CONFIRMED);
        saveReservation(todaySlot, 3, ReservationStatus.USED);
        saveReservation(todaySlot, 1, ReservationStatus.CANCELLED); // 빠짐
        TimeSlot yesterdaySlot = saveSlot(popup, today.minusDays(1), 1);
        saveReservation(yesterdaySlot, 1, ReservationStatus.USED);

        saveWaiting(popup, WaitingStatus.WAITING);
        saveWaiting(popup, WaitingStatus.CALLED);
        saveWaiting(popup, WaitingStatus.ENTERED); // 빠짐

        PopupStore otherPopup = savePopup(otherManager, 1); // 다른 매니저 팝업 : 빠짐
        saveReservation(saveSlot(otherPopup, today, 1), 1, ReservationStatus.CONFIRMED);
        saveWaiting(otherPopup, WaitingStatus.WAITING);
        flushAndClear();

        AdminDashboardResponse result = dashboardService.getDashboard(manager.getUsername());

        assertEquals(2, result.getTodayReservationCount());
        assertEquals(2, result.getCurrentWaitingCount());

        List<DailyReservationResponse> weekly = result.getWeeklyReservations();
        assertEquals(7, weekly.size());
        assertEquals(today, weekly.get(6).getDate()); // 마지막이 오늘
        assertEquals(2, weekly.get(6).getReserved());
        assertEquals(1, weekly.get(6).getEntered());
        assertEquals(1, weekly.get(5).getReserved()); // 어제
        assertEquals(1, weekly.get(5).getEntered());
        assertEquals(0, weekly.get(0).getReserved()); // 예약 없는 날은 0

        assertEquals(1, result.getPopupCrowding().size());
        assertEquals(popup.getPopId(), result.getPopupCrowding().get(0).getPopId());
        assertEquals(50, result.getPopupCrowding().get(0).getPercent());
        assertEquals(2, result.getPopupCrowding().get(0).getWaiting());
    }

    @Test
    @DisplayName("대시보드 : 결제 완료 주문 수, 최근 주문(결제 대기 제외, 본인 상품 금액만), 재고 부족 상품")
    void dashboard_manager_orderAndStock() {
        PopupStore popup = savePopup(manager, 1);
        PopupStore otherPopup = savePopup(otherManager, 1);
        PopupStore cancelledPopup = savePopup(manager, 2);
        cancelledPopup.cancel();

        Product myProduct = saveProduct(popup, "내 머그컵", 3);        // 재고 부족
        Product enoughProduct = saveProduct(popup, "내 키링", 10);     // 재고 충분
        Product otherProduct = saveProduct(otherPopup, "남의 티셔츠", 50);
        saveProduct(cancelledPopup, "취소 팝업 상품", 1);              // 운영 취소 팝업 : 빠짐

        Order mixedOrder = saveOrder(true, myProduct, otherProduct); // 결제 완료, 다른 팝업 상품 섞임
        saveOrder(false, enoughProduct);                             // 결제 대기 : 최근 주문에서 빠짐
        flushAndClear();

        AdminDashboardResponse result = dashboardService.getDashboard(manager.getUsername());

        assertEquals(1, result.getTodayPaidOrderCount());

        assertEquals(1, result.getRecentOrders().size());
        assertEquals(mixedOrder.getOrderId(), result.getRecentOrders().get(0).getOrderId());
        assertEquals("내 머그컵", result.getRecentOrders().get(0).getProductName());
        assertEquals(10000, result.getRecentOrders().get(0).getPrice()); // 남의 상품 금액은 빠짐
        assertEquals("PAID", result.getRecentOrders().get(0).getStatus());

        assertEquals(1, result.getLowStockCount());
        assertEquals(myProduct.getPrdId(), result.getLowStockProducts().get(0).getPrdId());
    }

    // ------------------------- 대시보드 (ADMIN) -------------------------

    @Test
    @DisplayName("대시보드 ADMIN : 모든 매니저 팝업이 집계됨 (넣기 전과 넣은 후의 차이로 확인)")
    void dashboard_admin_countsAllPopups() {
        AdminDashboardResponse before = dashboardService.getDashboard(admin.getUsername());

        PopupStore popup = savePopup(manager, 1);
        PopupStore otherPopup = savePopup(otherManager, 1);
        saveReservation(saveSlot(popup, today, 1), 1, ReservationStatus.CONFIRMED);
        saveReservation(saveSlot(otherPopup, today, 1), 1, ReservationStatus.CONFIRMED);
        saveWaiting(popup, WaitingStatus.WAITING);
        saveWaiting(otherPopup, WaitingStatus.WAITING);
        saveOrder(true, saveProduct(popup, "내 머그컵", 10));
        saveOrder(true, saveProduct(otherPopup, "남의 티셔츠", 10));
        flushAndClear();

        AdminDashboardResponse after = dashboardService.getDashboard(admin.getUsername());

        assertEquals(2, after.getTodayReservationCount() - before.getTodayReservationCount());
        assertEquals(2, after.getCurrentWaitingCount() - before.getCurrentWaitingCount());
        assertEquals(2, after.getTodayPaidOrderCount() - before.getTodayPaidOrderCount());
    }
}

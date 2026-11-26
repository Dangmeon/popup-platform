package com.popq.platform.dashboard;

import com.popq.platform.auth.entity.Role;
import com.popq.platform.auth.entity.User;
import com.popq.platform.dashboard.dto.AdminDashboardResponse;
import com.popq.platform.dashboard.dto.DailyReservationResponse;
import com.popq.platform.dashboard.dto.PopupRankingResponse;
import com.popq.platform.dashboard.dto.RecentOrderResponse;
import com.popq.platform.dashboard.repository.*;
import com.popq.platform.dashboard.service.AdminDashboardService;
import com.popq.platform.order.entity.Order;
import com.popq.platform.order.entity.OrderItem;
import com.popq.platform.popup.entity.PopupStore;
import com.popq.platform.popup.exception.PopupErrorCode;
import com.popq.platform.popup.exception.PopupException;
import com.popq.platform.popup.service.PopupAccessService;
import com.popq.platform.product.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// AdminDashboardService 단위 테스트 : 집계 쿼리(Repository)는 Mock으로 만들고, 서비스의 계산·가공 로직만 확인
@ExtendWith(MockitoExtension.class)
public class AdminDashboardServiceTest {

    @Mock
    AdminDashboardRepository dashboardRepo;
    @Mock
    PopupAccessService accessService;

    AdminDashboardService dashboardService;

    static final String ADMIN = "admin@gmail.com";
    static final String MANAGER = "manager@gmail.com";

    @BeforeEach
    void setUp() {
        dashboardService = new AdminDashboardService(dashboardRepo, accessService);
    }

    // ------------------------- 테스트 데이터 만들기 -------------------------
    // Projection은 인터페이스라서 Mock으로 만들고 필요한 값만 넣음 (lenient : 안 쓰는 값이 있어도 에러 안 냄)

    private PopupRankingProjection rankingRow(Long popId, String title, long people) {
        PopupRankingProjection row = mock(PopupRankingProjection.class, withSettings().strictness(Strictness.LENIENT));
        when(row.getPopId()).thenReturn(popId);
        when(row.getTitle()).thenReturn(title);
        when(row.getReservedPeople()).thenReturn(people);
        return row;
    }

    private WaitingCountProjection waitingRow(Long popId, long count) {
        WaitingCountProjection row = mock(WaitingCountProjection.class, withSettings().strictness(Strictness.LENIENT));
        when(row.getPopId()).thenReturn(popId);
        when(row.getWaitingCount()).thenReturn(count);
        return row;
    }

    private PopupCrowdingProjection crowdingRow(Long popId, Long capacity, Long reserved) {
        PopupCrowdingProjection row = mock(PopupCrowdingProjection.class, withSettings().strictness(Strictness.LENIENT));
        when(row.getPopId()).thenReturn(popId);
        when(row.getTitle()).thenReturn("팝업" + popId);
        when(row.getCapacity()).thenReturn(capacity);
        when(row.getReserved()).thenReturn(reserved);
        return row;
    }

    private DailyReservationProjection dailyRow(LocalDate date, long reserved, Long entered) {
        DailyReservationProjection row = mock(DailyReservationProjection.class, withSettings().strictness(Strictness.LENIENT));
        when(row.getSlotDate()).thenReturn(date);
        when(row.getReserved()).thenReturn(reserved);
        when(row.getEntered()).thenReturn(entered);
        return row;
    }

    // 담당 매니저 id가 있는 팝업의 상품
    private Product product(String name, Long managerId) {
        User manager = User.builder().username("m" + managerId + "@gmail.com").role(Role.MANAGER).build();
        ReflectionTestUtils.setField(manager, "id", managerId);
        PopupStore popup = PopupStore.builder().popId(managerId).title("팝업").build();
        popup.assignManager(manager);
        return Product.builder().productName(name).popupStore(popup).stock(1).build();
    }

    // 예약 많은 순 팝업 목록

    @Test
    @DisplayName("랭킹 : limit만큼만 잘라서 1위부터 순위를 붙임")
    void ranking_limit() {
        when(accessService.getManagerIdForList(ADMIN)).thenReturn(null);
        List<PopupRankingProjection> rows = List.of(rankingRow(1L, "A", 30), rankingRow(2L, "B", 20), rankingRow(3L, "C", 10));
        when(dashboardRepo.findReservationRanking(isNull(), anyList(), any(LocalDateTime.class))).thenReturn(rows);

        List<PopupRankingResponse> result = dashboardService.listReservationRanking(ADMIN, null, 2);

        assertEquals(2, result.size());
        assertEquals(1, result.get(0).getRank());
        assertEquals("A", result.get(0).getTitle());
        assertEquals(2, result.get(1).getRank());
    }

    @Test
    @DisplayName("랭킹 : days가 없으면 전체 누적, days=7이면 오늘 포함 최근 7일 예약만")
    void ranking_days() {
        when(accessService.getManagerIdForList(ADMIN)).thenReturn(null);

        dashboardService.listReservationRanking(ADMIN, null, null);
        dashboardService.listReservationRanking(ADMIN, 7, null);

        verify(dashboardRepo).findReservationRanking(isNull(), anyList(), eq(LocalDateTime.of(2000, 1, 1, 0, 0)));
        verify(dashboardRepo).findReservationRanking(isNull(), anyList(), eq(LocalDate.now().minusDays(6).atStartOfDay()));
    }

    @Test
    @DisplayName("랭킹 : MANAGER면 본인 id로 담당 팝업만 조회")
    void ranking_manager() {
        when(accessService.getManagerIdForList(MANAGER)).thenReturn(2L);

        dashboardService.listReservationRanking(MANAGER, null, null);

        verify(dashboardRepo).findReservationRanking(eq(2L), anyList(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("랭킹 실패 : 일반 회원이면 POPUP_ACCESS_DENIED, 집계 쿼리는 실행하지 않음")
    void ranking_user() {
        when(accessService.getManagerIdForList("user@gmail.com")).thenThrow(new PopupException(PopupErrorCode.POPUP_ACCESS_DENIED));

        PopupException e = assertThrows(PopupException.class,
                () -> dashboardService.listReservationRanking("user@gmail.com", null, null));
        assertEquals(PopupErrorCode.POPUP_ACCESS_DENIED, e.getErrorCode());
        verify(dashboardRepo, never()).findReservationRanking(any(), anyList(), any());
    }

    // ------------------------- 관리자 대시보드 -------------------------

    @Test
    @DisplayName("대시보드 : 현재 웨이팅 수는 팝업별 웨이팅 합계")
    void dashboard_waitingSum() {
        when(accessService.getManagerIdForList(ADMIN)).thenReturn(null);
        List<WaitingCountProjection> waitings = List.of(waitingRow(1L, 3), waitingRow(2L, 4));
        when(dashboardRepo.countWaitingsByPopup(isNull(), any(), anyList())).thenReturn(waitings);

        AdminDashboardResponse response = dashboardService.getDashboard(ADMIN);

        assertEquals(7, response.getCurrentWaitingCount());
    }

    @Test
    @DisplayName("대시보드 : 혼잡도 = 예약 인원 / 정원 x 100 반올림, 정원 0이면 0%, 팝업별 웨이팅 수 표시")
    void dashboard_crowding() {
        when(accessService.getManagerIdForList(ADMIN)).thenReturn(null);
        List<WaitingCountProjection> waitings = List.of(waitingRow(1L, 5));
        when(dashboardRepo.countWaitingsByPopup(isNull(), any(), anyList())).thenReturn(waitings);
        List<PopupCrowdingProjection> crowding = List.of(crowdingRow(1L, 8L, 3L), crowdingRow(2L, 0L, 0L));
        when(dashboardRepo.findTodayCrowding(isNull(), any(LocalDate.class))).thenReturn(crowding);

        AdminDashboardResponse response = dashboardService.getDashboard(ADMIN);

        assertEquals(38, response.getPopupCrowding().get(0).getPercent()); // 3 / 8 = 37.5% -> 38
        assertEquals(5, response.getPopupCrowding().get(0).getWaiting());
        assertEquals(0, response.getPopupCrowding().get(1).getPercent());
        assertEquals(0, response.getPopupCrowding().get(1).getWaiting()); // 웨이팅 없는 팝업은 0
    }

    @Test
    @DisplayName("대시보드 : 최근 7일 추이는 예약 없는 날도 0으로 채워서 항상 7개 (오래된 날짜부터)")
    void dashboard_weekly() {
        when(accessService.getManagerIdForList(ADMIN)).thenReturn(null);
        LocalDate today = LocalDate.now();
        List<DailyReservationProjection> rows = List.of(dailyRow(today, 10, 4L), dailyRow(today.minusDays(6), 2, null));
        when(dashboardRepo.countDailyReservations(isNull(), any(), any(), anyList(), any())).thenReturn(rows);

        List<DailyReservationResponse> weekly = dashboardService.getDashboard(ADMIN).getWeeklyReservations();

        assertEquals(7, weekly.size());
        assertEquals(today.minusDays(6), weekly.get(0).getDate());
        assertEquals(2, weekly.get(0).getReserved());
        assertEquals(0, weekly.get(0).getEntered()); // 입장 수가 null이면 0
        assertEquals(0, weekly.get(3).getReserved()); // 예약 없는 날
        assertEquals(10, weekly.get(6).getReserved());
        assertEquals(4, weekly.get(6).getEntered());
    }

    @Test
    @DisplayName("대시보드 : 재고 부족 상품 수는 전체 개수, 목록은 최대 5개")
    void dashboard_lowStock() {
        when(accessService.getManagerIdForList(ADMIN)).thenReturn(null);
        List<Product> lowStock = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            lowStock.add(product("굿즈" + i, 2L));
        }
        when(dashboardRepo.findLowStockProducts(isNull(), eq(5), any(LocalDateTime.class))).thenReturn(lowStock);

        AdminDashboardResponse response = dashboardService.getDashboard(ADMIN);

        assertEquals(7, response.getLowStockCount());
        assertEquals(5, response.getLowStockProducts().size());
    }

    @Test
    @DisplayName("대시보드 : 최근 주문은 최대 5건, ADMIN은 주문 총액 표시")
    void dashboard_recentOrdersAdmin() {
        when(accessService.getManagerIdForList(ADMIN)).thenReturn(null);
        List<Order> orders = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            Order order = Order.createOrder(null, 30000, null, "수령인", "010-0000-0000", "주소", null, null, null);
            order.addOrderItem(OrderItem.createOrderItem(product("굿즈A", 2L), 1, 10000, 10000));
            order.addOrderItem(OrderItem.createOrderItem(product("굿즈B", 3L), 1, 20000, 20000));
            orders.add(order);
        }
        when(dashboardRepo.findRecentOrders(isNull(), any())).thenReturn(orders);

        List<RecentOrderResponse> recent = dashboardService.getDashboard(ADMIN).getRecentOrders();

        assertEquals(5, recent.size());
        assertEquals("굿즈A 외 1건", recent.get(0).getProductName());
        assertEquals(30000, recent.get(0).getPrice());
    }

    @Test
    @DisplayName("대시보드 : MANAGER의 최근 주문은 담당 팝업 상품 이름·금액만 표시")
    void dashboard_recentOrdersManager() {
        when(accessService.getManagerIdForList(MANAGER)).thenReturn(2L);
        Order order = Order.createOrder(null, 30000, null, "수령인", "010-0000-0000", "주소", null, null, null);
        order.addOrderItem(OrderItem.createOrderItem(product("다른 팝업 굿즈", 3L), 1, 20000, 20000));
        order.addOrderItem(OrderItem.createOrderItem(product("내 팝업 굿즈", 2L), 1, 10000, 10000));
        when(dashboardRepo.findRecentOrders(eq(2L), any())).thenReturn(List.of(order));

        RecentOrderResponse recent = dashboardService.getDashboard(MANAGER).getRecentOrders().get(0);

        assertEquals("내 팝업 굿즈", recent.getProductName());
        assertEquals(10000, recent.getPrice()); // 다른 팝업 상품 20000원은 빠짐
    }

    @Test
    @DisplayName("대시보드 실패 : 일반 회원이면 POPUP_ACCESS_DENIED")
    void dashboard_user() {
        when(accessService.getManagerIdForList("user@gmail.com")).thenThrow(new PopupException(PopupErrorCode.POPUP_ACCESS_DENIED));

        PopupException e = assertThrows(PopupException.class, () -> dashboardService.getDashboard("user@gmail.com"));
        assertEquals(PopupErrorCode.POPUP_ACCESS_DENIED, e.getErrorCode());
    }
}

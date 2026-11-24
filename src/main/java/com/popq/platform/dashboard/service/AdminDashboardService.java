package com.popq.platform.dashboard.service;

import com.popq.platform.dashboard.dto.AdminDashboardResponse;
import com.popq.platform.dashboard.dto.DailyReservationResponse;
import com.popq.platform.dashboard.dto.LowStockProductResponse;
import com.popq.platform.dashboard.dto.PopupCrowdingResponse;
import com.popq.platform.dashboard.dto.PopupRankingResponse;
import com.popq.platform.dashboard.dto.RecentOrderResponse;
import com.popq.platform.product.entity.Product;
import com.popq.platform.order.entity.Order;
import com.popq.platform.order.entity.OrderItem;
import com.popq.platform.order.entity.OrderStatus;
import com.popq.platform.dashboard.repository.AdminDashboardRepository;
import com.popq.platform.dashboard.repository.DailyReservationProjection;
import com.popq.platform.dashboard.repository.PopupCrowdingProjection;
import com.popq.platform.dashboard.repository.PopupRankingProjection;
import com.popq.platform.dashboard.repository.WaitingCountProjection;
import com.popq.platform.reservation.entity.ReservationStatus;
import com.popq.platform.popup.service.PopupAccessService;
import com.popq.platform.waiting.entity.WaitingStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 관리자 대시보드 · 예약 많은 순 팝업 목록
// ADMIN : 전체 팝업 기준 / MANAGER : 본인 담당 팝업 기준 / 그 외 403
@Service
@Transactional(readOnly = true)
public class AdminDashboardService {
    AdminDashboardRepository dashboardRepo;
    PopupAccessService accessService;

    // 집계에 넣을 예약 상태 : 확정 + 입장 완료
    static final List<ReservationStatus> RESERVED_STATUSES = List.of(ReservationStatus.CONFIRMED, ReservationStatus.USED);
    // 현재 웨이팅으로 볼 상태 : 대기 + 호출
    static final List<WaitingStatus> WAITING_STATUSES = List.of(WaitingStatus.WAITING, WaitingStatus.CALLED);

    static final int LOW_STOCK_THRESHOLD = 5;   // 재고 부족 기준 (5개 이하)
    static final int LOW_STOCK_LIST_SIZE = 5;   // 재고 부족 목록 최대 개수
    static final int RECENT_ORDER_SIZE = 5;     // 최근 주문 개수
    static final int DEFAULT_RANKING_LIMIT = 10;
    static final int MAX_RANKING_LIMIT = 50;
    static final String[] DAY_NAMES = {"월", "화", "수", "목", "금", "토", "일"};

    @Autowired
    public AdminDashboardService(AdminDashboardRepository dashboardRepo, PopupAccessService accessService) {
        this.dashboardRepo = dashboardRepo;
        this.accessService = accessService;
    }

    // 예약 많은 순 팝업 목록
    // days : 없으면 전체 누적 / 7이면 오늘 포함 최근 7일 동안 들어온 예약만
    // limit : 없으면 10개, 최대 50개
    public List<PopupRankingResponse> listReservationRanking(String username, Integer days, Integer limit) {
        Long managerId = accessService.getManagerIdForList(username); // ADMIN null, MANAGER 본인 id, 그 외 403

        LocalDateTime fromTime = LocalDateTime.of(2000, 1, 1, 0, 0); // 전체 누적
        if (days != null && days > 0) {
            fromTime = LocalDate.now().minusDays(days - 1).atStartOfDay();
        }
        int size = DEFAULT_RANKING_LIMIT;
        if (limit != null && limit > 0) {
            size = Math.min(limit, MAX_RANKING_LIMIT);
        }

        List<PopupRankingResponse> dtoList = new ArrayList<PopupRankingResponse>();
        int rank = 1;
        for (PopupRankingProjection row : dashboardRepo.findReservationRanking(managerId, RESERVED_STATUSES, fromTime)) {
            if (rank > size) {
                break;
            }
            dtoList.add(PopupRankingResponse.toDto(rank, row));
            rank++;
        }
        return dtoList;
    }

    // 관리자 대시보드
    public AdminDashboardResponse getDashboard(String username) {
        Long managerId = accessService.getManagerIdForList(username); // ADMIN null, MANAGER 본인 id, 그 외 403

        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();

        // 1. 상단 숫자
        long todayReservationCount = dashboardRepo.countTodayReservations(managerId, today, RESERVED_STATUSES);
        long todayPaidOrderCount = dashboardRepo.countTodayPaidOrders(managerId, todayStart, tomorrowStart, OrderStatus.PAID);

        List<WaitingCountProjection> waitingRows = dashboardRepo.countWaitingsByPopup(managerId, todayStart, WAITING_STATUSES);
        long currentWaitingCount = 0;
        for (WaitingCountProjection row : waitingRows) {
            currentWaitingCount += row.getWaitingCount();
        }

        // 2. 최근 7일 예약 · 입장 추이
        List<DailyReservationResponse> weekly = getWeeklyReservations(managerId, today);

        // 3. 팝업별 혼잡도
        List<PopupCrowdingResponse> crowding = new ArrayList<PopupCrowdingResponse>();
        for (PopupCrowdingProjection row : dashboardRepo.findTodayCrowding(managerId, today)) {
            long capacity = row.getCapacity() == null ? 0 : row.getCapacity();
            long reserved = row.getReserved() == null ? 0 : row.getReserved();
            int percent = 0;
            if (capacity > 0) {
                percent = (int) Math.round(reserved * 100.0 / capacity);
            }
            crowding.add(PopupCrowdingResponse.builder()
                    .popId(row.getPopId())
                    .title(row.getTitle())
                    .percent(percent)
                    .waiting(findWaitingCount(waitingRows, row.getPopId()))
                    .build());
        }

        // 4. 최근 주문 5건
        List<RecentOrderResponse> recentOrders = new ArrayList<RecentOrderResponse>();
        for (Order order : dashboardRepo.findRecentOrders(managerId, OrderStatus.PENDING)) {
            if (recentOrders.size() >= RECENT_ORDER_SIZE) {
                break;
            }
            recentOrders.add(toRecentOrder(order, managerId));
        }

        // 5. 재고 부족 상품 (개수는 전체, 목록은 5개까지)
        List<Product> lowStockList = dashboardRepo.findLowStockProducts(managerId, LOW_STOCK_THRESHOLD, LocalDateTime.now());
        List<LowStockProductResponse> lowStockProducts = new ArrayList<LowStockProductResponse>();
        for (Product product : lowStockList) {
            if (lowStockProducts.size() >= LOW_STOCK_LIST_SIZE) {
                break;
            }
            lowStockProducts.add(LowStockProductResponse.toDto(product));
        }

        return AdminDashboardResponse.builder()
                .todayReservationCount(todayReservationCount)
                .currentWaitingCount(currentWaitingCount)
                .todayPaidOrderCount(todayPaidOrderCount)
                .lowStockCount(lowStockList.size())
                .weeklyReservations(weekly)
                .popupCrowding(crowding)
                .recentOrders(recentOrders)
                .lowStockProducts(lowStockProducts)
                .build();
    }

    // 최근 7일 (6일 전 ~ 오늘) : 예약이 없는 날도 0으로 채움
    private List<DailyReservationResponse> getWeeklyReservations(Long managerId, LocalDate today) {
        LocalDate fromDate = today.minusDays(6);
        List<DailyReservationProjection> rows =
                dashboardRepo.countDailyReservations(managerId, fromDate, today, RESERVED_STATUSES, ReservationStatus.USED);

        List<DailyReservationResponse> weekly = new ArrayList<DailyReservationResponse>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = fromDate.plusDays(i);
            long reserved = 0;
            long entered = 0;
            for (DailyReservationProjection row : rows) {
                if (row.getSlotDate().equals(date)) {
                    reserved = row.getReserved();
                    entered = row.getEntered() == null ? 0 : row.getEntered();
                }
            }
            weekly.add(DailyReservationResponse.builder()
                    .date(date)
                    .day(DAY_NAMES[date.getDayOfWeek().getValue() - 1]) // 월요일 = 1
                    .reserved(reserved)
                    .entered(entered)
                    .build());
        }
        return weekly;
    }

    // 팝업의 현재 웨이팅 수 (없으면 0)
    private long findWaitingCount(List<WaitingCountProjection> waitingRows, Long popId) {
        for (WaitingCountProjection row : waitingRows) {
            if (row.getPopId().equals(popId)) {
                return row.getWaitingCount();
            }
        }
        return 0;
    }

    // 주문 -> 최근 주문 응답
    // MANAGER는 다른 팝업 상품 금액이 섞이지 않도록 담당 팝업 상품만 합산
    private RecentOrderResponse toRecentOrder(Order order, Long managerId) {
        List<OrderItem> items = new ArrayList<OrderItem>();
        for (OrderItem item : order.getOrderItems()) {
            if (managerId == null || isManagedBy(item, managerId)) {
                items.add(item);
            }
        }

        String productName = "";
        if (!items.isEmpty()) {
            productName = items.get(0).getProduct().getProductName();
            if (items.size() > 1) {
                productName = productName + " 외 " + (items.size() - 1) + "건";
            }
        }

        int price = 0;
        if (managerId == null) {
            price = order.getTotalPrice();
        } else {
            for (OrderItem item : items) {
                price += item.getPrice();
            }
        }

        return RecentOrderResponse.builder()
                .orderId(order.getOrderId())
                .productName(productName)
                .price(price)
                .status(order.getOrderStatus().name())
                .build();
    }

    // 주문 상품이 이 매니저의 담당 팝업 상품인지
    private boolean isManagedBy(OrderItem item, Long managerId) {
        if (item.getProduct().getPopupStore() == null || item.getProduct().getPopupStore().getManager() == null) {
            return false;
        }
        return managerId.equals(item.getProduct().getPopupStore().getManager().getId());
    }
}
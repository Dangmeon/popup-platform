package com.popq.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;


@Getter
@AllArgsConstructor
@Builder
public class AdminDashboardResponse { // 관리자 대시보드 응답
    private long todayReservationCount;   // 오늘 예약 수 (오늘 회차, CONFIRMED · USED)
    private long currentWaitingCount;     // 현재 웨이팅 수 (오늘 접수, WAITING · CALLED)
    private long todayPaidOrderCount;     // 오늘 결제 완료 주문 수
    private int lowStockCount;            // 재고 부족 상품 수

    private List<DailyReservationResponse> weeklyReservations; // 최근 7일 예약 · 입장 추이 (오래된 날짜부터)
    private List<PopupCrowdingResponse> popupCrowding;         // 팝업별 혼잡도
    private List<RecentOrderResponse> recentOrders;            // 최근 주문 5건
    private List<LowStockProductResponse> lowStockProducts;    // 재고 부족 상품 (최대 5개, 재고 적은 순)
}

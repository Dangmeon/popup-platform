package com.popq.platform.controller;

import com.popq.platform.dto.AdminDashboardResponse;
import com.popq.platform.dto.PopupRankingResponse;
import com.popq.platform.service.AdminDashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 관리자 대시보드 · 예약 많은 순 팝업 목록
// ADMIN은 전체 팝업, MANAGER는 본인 담당 팝업 기준 (서비스에서 확인)
@RestController
public class AdminDashboardController {
    AdminDashboardService service;

    @Autowired
    public AdminDashboardController(AdminDashboardService service) {
        this.service = service;
    }

    // 관리자 대시보드
    @GetMapping("/api/admin/dashboard")
    public AdminDashboardResponse getDashboard(@AuthenticationPrincipal String username) {
        return service.getDashboard(username);
    }

    // 예약 많은 순 팝업 목록
    // /api/admin/popups/ranking              : 전체 누적, 10개
    // /api/admin/popups/ranking?days=7       : 최근 7일 동안 들어온 예약 기준
    // /api/admin/popups/ranking?limit=5      : 5개만
    @GetMapping("/api/admin/popups/ranking")
    public List<PopupRankingResponse> listReservationRanking(
            @AuthenticationPrincipal String username,
            @RequestParam(value = "days", required = false) Integer days,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return service.listReservationRanking(username, days, limit);
    }
}

package com.popq.platform.sales.controller;

import com.popq.platform.sales.dto.*;
import com.popq.platform.sales.service.AdminSalesService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminSalesController {

    private final AdminSalesService adminSalesService;

    @GetMapping("/sales")
    public ResponseEntity<SalesSummaryResponse> getTotalSales(
            Authentication authentication
    ){
        String username = authentication.getName();

        return ResponseEntity.ok(adminSalesService.getTotalSales(username));
    }

    @GetMapping("/popups/{popId}/sales")
    public ResponseEntity<PopupSalesResponse> getPopupSales(
            Authentication authentication,
            @PathVariable Long popId
    ){
        String username = authentication.getName();

        return ResponseEntity.ok(adminSalesService.getPopupSales(username, popId));
    }

    @GetMapping("/monthly")
    public ResponseEntity<List<MonthlySalesResponse>> getMonthlySales(
            Authentication authentication
    ){
        String username = authentication.getName();

        return ResponseEntity.ok(adminSalesService.getMonthlySales(username));
    }

    @GetMapping("/popups/{popId}/weekly")
    public ResponseEntity<List<WeekSalesResponse>> getWeeklySales(
            Authentication authentication,
            @PathVariable Long popId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ){
        String username = authentication.getName();

        return ResponseEntity.ok(adminSalesService.getWeeklySales(username, popId, date));
    }

    /**
     * 플랫폼 전체 다음 달 예상 매출 조회 (ADMIN 전용)
     * - 기존 확정 매출 API(/api/admin/sales)와 엔드포인트를 분리하여,
     *   프론트엔드에서 기본 매출 숫자를 먼저 띄운 뒤 이 API만 비동기로 호출해 다음달 예상 매출 카드에만 로딩을 표시할 수 있게 험.
     * - ?refresh=true 파라미터 전달 시 서버 캐시를 무시하고 LLM을 재호출.
     */
    @GetMapping("/sales/forecast")
    public ResponseEntity<SalesForecastResponse> getTotalSalesForecast(
            Authentication authentication,
            @RequestParam(value = "refresh", defaultValue = "false") boolean refresh
    ) {
        SalesForecastResponse response =
                adminSalesService.forecastTotalSales(authentication.getName(), refresh);

        return ResponseEntity.ok(response);
    }

    /**
     * 개별 팝업 다음 달 예상 매출 조회 (ADMIN 전체 / MANAGER 담당 팝업)
     */
    @GetMapping("/popups/{popId}/sales/forecast")
    public ResponseEntity<SalesForecastResponse> getPopupSalesForecast(
            Authentication authentication,
            @PathVariable Long popId,
            @RequestParam(value = "refresh", defaultValue = "false") boolean refresh
    ) {
        SalesForecastResponse response =
                adminSalesService.forecastPopupSales(authentication.getName(), popId, refresh);

        return ResponseEntity.ok(response);
    }
}

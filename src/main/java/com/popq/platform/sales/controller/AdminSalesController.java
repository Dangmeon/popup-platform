package com.popq.platform.sales.controller;

import com.popq.platform.sales.dto.PopupSalesResponse;
import com.popq.platform.sales.dto.SalesSummaryResponse;
import com.popq.platform.sales.dto.SalesForecastResponse;
import com.popq.platform.sales.service.AdminSalesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminSalesController {

    private final AdminSalesService adminSalesService;

    @GetMapping("/sales")
    public ResponseEntity<SalesSummaryResponse> getTotalSales(
            Authentication authentication
    ){
        SalesSummaryResponse response =
                adminSalesService.getTotalSales(authentication.getName());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/popups/{popId}/sales")
    public ResponseEntity<PopupSalesResponse> getPopupSales(
            Authentication authentication,
            @PathVariable Long popId
    ){
        String username = authentication.getName();

        PopupSalesResponse response =
                adminSalesService.getPopupSales(username, popId);

        return ResponseEntity.ok(response);
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

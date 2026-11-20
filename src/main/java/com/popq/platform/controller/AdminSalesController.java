package com.popq.platform.controller;

import com.popq.platform.dto.PopupSalesResponse;
import com.popq.platform.dto.SalesSummaryResponse;
import com.popq.platform.service.AdminSalesService;
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


}

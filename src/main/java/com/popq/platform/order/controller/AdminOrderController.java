package com.popq.platform.order.controller;

import com.popq.platform.order.dto.AdminOrderDetailResponse;
import com.popq.platform.order.dto.AdminOrderResponse;
import com.popq.platform.order.service.OrderService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;

    @GetMapping
    public ResponseEntity<List<AdminOrderResponse>> getAllOrdersAdmin() {
        return ResponseEntity.ok(orderService.getAllOrdersAdmin());
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<AdminOrderDetailResponse> getOrderDetail(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.getAdminOrderDetail(orderId));
    }
}

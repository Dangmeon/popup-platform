package com.popq.platform.controller;

import com.popq.platform.dto.OrderCreateRequest;
import com.popq.platform.dto.OrderDetailResponse;
import com.popq.platform.dto.OrderResponse;
import com.popq.platform.service.OrderService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.support.SimpleTriggerContext;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            Authentication authentication,
            @RequestBody OrderCreateRequest request) {

        String username = authentication.getName();

        return ResponseEntity.ok(orderService.createOrder(username, request));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders(
            Authentication authentication
    ) {

        String username = authentication.getName();

        return ResponseEntity.ok(orderService.getMyOrders(username));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailResponse> getOrderDetail(
            Authentication authentication,
            @PathVariable Long orderId
    ) {
        String username = authentication.getName();

        return ResponseEntity.ok(orderService.getOrderDetail(username, orderId));
    }

    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            Authentication authentication,
            @PathVariable Long orderId
    ) {
        String username = authentication.getName();

        return ResponseEntity.ok(orderService.cancelOrder(username, orderId));
    }
}

package com.popq.platform.order.controller;

import com.popq.platform.order.dto.OrderCreateRequest;
import com.popq.platform.order.dto.OrderDetailResponse;
import com.popq.platform.order.dto.OrderResponse;
import com.popq.platform.order.dto.RecentDeliveryResponse;
import com.popq.platform.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
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

    @GetMapping("/recent-delivery")
    public ResponseEntity<RecentDeliveryResponse> getRecentDelivery(
            Authentication authentication
    ){
        String username = authentication.getName();

        return ResponseEntity.ok(orderService.getRecentDelivery(username));
    }
}

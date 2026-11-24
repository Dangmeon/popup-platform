package com.popq.platform.controller;

import com.popq.platform.dto.CartQuantityUpdateRequest;
import com.popq.platform.dto.CartRequest;
import com.popq.platform.dto.CartResponse;
import com.popq.platform.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    // 내 장바구니 조회
    @GetMapping
    public ResponseEntity<List<CartResponse>> getCart(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                cartService.getCart(getUsername(authentication))
        );
    }

    // 상품 추가
    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
            Authentication authentication,
            @Valid @RequestBody CartRequest request
    ) {
        return ResponseEntity.ok(
                cartService.addItem(
                        getUsername(authentication),
                        request
                )
        );
    }

    // 상품 수량 변경
    @PatchMapping("/items/{cartItemId}")
    public ResponseEntity<CartResponse> updateQuantity(
            Authentication authentication,
            @PathVariable("cartItemId") Long cartItemId,
            @Valid @RequestBody CartQuantityUpdateRequest request
    ) {
        return ResponseEntity.ok(
                cartService.updateQuantity(
                        getUsername(authentication),
                        cartItemId,
                        request.getQuantity()
                )
        );
    }

    // 상품 삭제
    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<Void> removeItem(
            Authentication authentication,
            @PathVariable("cartItemId") Long cartItemId
    ) {
        cartService.removeItem(
                getUsername(authentication),
                cartItemId
        );

        return ResponseEntity.noContent().build();
    }

    // 인증된 로그인 아이디
    private String getUsername(Authentication authentication) {
        return authentication != null
                ? authentication.getName()
                : null;
    }
}
package com.popq.platform.mypage.controller;

import com.popq.platform.mypage.dto.MyFavoriteResponse;
import com.popq.platform.mypage.dto.MyReservationResponse;
import com.popq.platform.mypage.service.MyPageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mypage")
@RequiredArgsConstructor
public class MyPageController {

    private final MyPageService myPageService;

    @GetMapping("/reservations")
    public ResponseEntity<List<MyReservationResponse>> getMyReservations(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(myPageService.getMyReservations(email));
    }

    @GetMapping("/favorites")
    public ResponseEntity<List<MyFavoriteResponse>> getMyFavorites(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(myPageService.getMyFavorites(email));
    }
}
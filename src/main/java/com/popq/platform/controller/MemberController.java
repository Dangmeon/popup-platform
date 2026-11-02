package com.popq.platform.controller;

import com.popq.platform.dto.MemberInfoResponse;
import com.popq.platform.dto.MemberUpdateRequest;
import com.popq.platform.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final UserService userService;

    // 내 정보 조회
    @GetMapping("/me")
    public ResponseEntity<MemberInfoResponse> getMyInfo(@AuthenticationPrincipal String email) {
        MemberInfoResponse responseDto = userService.getMyInfo(email);
        return ResponseEntity.ok(responseDto);
    }

    // 내 정보 수정 (비밀번호, 이름, 전화번호)
    @PutMapping("/me")
    public ResponseEntity<MemberInfoResponse> updateMyInfo(
            @AuthenticationPrincipal String email,
            @RequestBody MemberUpdateRequest requestDto) {
        MemberInfoResponse responseDto = userService.updateMyInfo(email, requestDto);
        return ResponseEntity.ok(responseDto);
    }
}
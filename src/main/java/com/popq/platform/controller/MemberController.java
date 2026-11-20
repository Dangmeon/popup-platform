package com.popq.platform.controller;

import com.popq.platform.dto.MemberInfoResponse;
import com.popq.platform.dto.MemberUpdateRequest;
import com.popq.platform.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final UserService userService;

    // 관리자 전체 회원 목록 조회(페이징)
    @GetMapping("/api/admin/members")
    public ResponseEntity<Page<MemberInfoResponse>> getAllMembers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(userService.getAllMembers(pageable));
    }

    // 내 정보 조회
    @GetMapping("/me")
    public ResponseEntity<MemberInfoResponse> getMyInfo(@AuthenticationPrincipal String email) {
        MemberInfoResponse responseDto = userService.getMyInfo(email);
        return ResponseEntity.ok(responseDto);
    }

    // 내 정보 수정
    @PutMapping("/me")
    public ResponseEntity<MemberInfoResponse> updateMyInfo(
            @AuthenticationPrincipal String email,
            @RequestBody MemberUpdateRequest requestDto) {
        MemberInfoResponse responseDto = userService.updateMyInfo(email, requestDto);
        return ResponseEntity.ok(responseDto);
    }
}
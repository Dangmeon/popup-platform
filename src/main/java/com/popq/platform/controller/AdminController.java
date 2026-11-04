package com.popq.platform.controller;

import com.popq.platform.dto.MemberInfoResponse;
import com.popq.platform.dto.RoleUpdate;
import com.popq.platform.dto.AdminRoleUpdateResponse;
import com.popq.platform.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    /**
     * 1. 관리자 회원 목록 조회
     * GET /api/admin/members?page=0&size=10
     */
    @GetMapping("/members")
    public ResponseEntity<Page<MemberInfoResponse>> getAllMembers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<MemberInfoResponse> members = adminService.getAllMembers(pageable);
        return ResponseEntity.ok(members);
    }

    /**
     * 2. 관리자 회원 권한 수정
     * PUT /api/admin/members/{memberId}/role
     */
    @PutMapping("/members/{memberId}/role")
    public ResponseEntity<AdminRoleUpdateResponse> updateMemberRole(
            @PathVariable Long memberId,
            @RequestBody RoleUpdate requestDto) {

        AdminRoleUpdateResponse response = adminService.updateMemberRole(memberId, requestDto.getRole());
        return ResponseEntity.ok(response);
    }
}
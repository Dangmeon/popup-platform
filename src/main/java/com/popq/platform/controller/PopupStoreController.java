package com.popq.platform.controller;

import com.popq.platform.dto.ManagerResponse;
import com.popq.platform.dto.PopupManagerRequest;
import com.popq.platform.dto.PopupStoreRequest;
import com.popq.platform.dto.PopupStoreResponse;
import com.popq.platform.service.PopupStoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class PopupStoreController {

    PopupStoreService service;

    @Autowired
    public PopupStoreController(PopupStoreService service) {
        this.service = service;
    }

    // 팝업 목록 조회
    // /api/popups                  : 전체
    // /api/popups?status=ONGOING   : 진행중
    // /api/popups?category=FASHION : 카테고리별
    // [회원 연동] 로그인한 회원이면 찜(favorite) 여부를 함께 반환
    @GetMapping("/api/popups")
    public List<PopupStoreResponse> listPopup(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "category", required = false) String category) {
        if ("ONGOING".equals(status)) {
            return service.listOngoingPopup();
        }
        if (category != null) {
            return service.listPopupByCategory(category);
        }
        return service.listAllPopup();
    }

    // [회원 연동] 로그인한 회원이면 찜(favorite) 여부를 함께 반환
    // - favorite 테이블 (target_type = 'POPUP', pop_id) 조회

    // 팝업 상세 조회
    @GetMapping("/api/popups/{popId}")
    public PopupStoreResponse detailViewPopup(@PathVariable("popId") Long popId) {
        return service.detailViewPopup(popId);
    }

    // 아래는 /api/admin/** 관리자 API : @AuthenticationPrincipal = 로그인한 회원 이메일
    // ADMIN은 모든 팝업, MANAGER는 본인 팝업만 (서비스에서 확인)

    // 관리자 팝업 목록
    // /api/admin/popups                    : 전체 (MANAGER는 본인 팝업만)
    // /api/admin/popups?keyword=홍대       : 팝업명·장소 검색
    // /api/admin/popups?opStatus=PAUSED    : 운영 상태 필터
    @GetMapping("/api/admin/popups")
    public List<PopupStoreResponse> listAdminPopup(
            @AuthenticationPrincipal String username,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "opStatus", required = false) String opStatus) {
        return service.listAdminPopup(username, keyword, opStatus);
    }

    // 팝업 등록 (MANAGER가 등록하면 본인이 담당 매니저)
    @PostMapping("/api/admin/popups")
    public PopupStoreResponse insertPopup(@RequestBody PopupStoreRequest request,
                                          @AuthenticationPrincipal String username) {
        return service.insertPopup(request, username); // 등록된 팝업 정보 반환
    }

    // 팝업 수정
    @PutMapping("/api/admin/popups/{popId}")
    public PopupStoreResponse updatePopup(@PathVariable("popId") Long popId, @RequestBody PopupStoreRequest request,
                                          @AuthenticationPrincipal String username) {
        return service.updatePopup(popId, request, username);
    }

    // 팝업 삭제
    @DeleteMapping("/api/admin/popups/{popId}")
    public void deletePopup(@PathVariable("popId") Long popId, @AuthenticationPrincipal String username) {
        service.deletePopup(popId, username);
    }

    // 매니저 목록 (ADMIN 전용) : 배정 드롭다운에 띄울 MANAGER 회원 [{id, name, email}]
    @GetMapping("/api/admin/managers")
    public List<ManagerResponse> listManagers(@AuthenticationPrincipal String username) {
        return service.listManagers(username);
    }

    // 담당 매니저 배정 (ADMIN 전용) : {"managerEmail": "..."} / 빈 값이면 배정 해제
    @PatchMapping("/api/admin/popups/{popId}/manager")
    public PopupStoreResponse assignManager(@PathVariable("popId") Long popId, @RequestBody PopupManagerRequest request,
                                            @AuthenticationPrincipal String username) {
        return service.assignManager(popId, request, username);
    }
}
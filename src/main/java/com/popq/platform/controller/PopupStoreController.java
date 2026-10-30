package com.popq.platform.controller;

import com.popq.platform.dto.PopupStoreRequest;
import com.popq.platform.dto.PopupStoreResponse;
import com.popq.platform.service.PopupStoreService;
import org.springframework.beans.factory.annotation.Autowired;
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

    // 아래 등록/수정/삭제는 /api/admin/** 이라 SecurityConfig에서 ADMIN만 호출 가능

    // 팝업 등록
    @PostMapping("/api/admin/popups")
    public PopupStoreResponse insertPopup(@RequestBody PopupStoreRequest request) {
        return service.insertPopup(request); // 등록된 팝업 정보 반환
    }

    // 팝업 수정
    @PutMapping("/api/admin/popups/{popId}")
    public void updatePopup(@PathVariable("popId") Long popId, @RequestBody PopupStoreRequest request) {
        service.updatePopup(popId, request);
    }

    // 팝업 삭제
    @DeleteMapping("/api/admin/popups/{popId}")
    public void deletePopup(@PathVariable("popId") Long popId) {
        service.deletePopup(popId);
    }
}
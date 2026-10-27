package com.popq.platform.controller;

import com.popq.platform.dto.PopupStoreRequest;
import com.popq.platform.dto.PopupStoreResponse;
import com.popq.platform.service.PopupStoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin("*")
@RestController
public class PopupStoreController {

    PopupStoreService service;

    @Autowired
    public PopupStoreController(PopupStoreService service) {
        this.service = service;
    }

    // 팝업 목록 조회
    // /popup/popupList                  : 전체
    // /popup/popupList?status=ONGOING   : 진행중
    // /popup/popupList?category=FASHION : 카테고리별
    // [회원 연동] 로그인한 회원이면 찜(favorite) 여부를 함께 반환
    @GetMapping("/popup/popupList")
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
    @GetMapping("/popup/popupDetailView/{popId}")
    public PopupStoreResponse detailViewPopup(@PathVariable("popId") Long popId) {
        return service.detailViewPopup(popId);
    }

    // [회원 연동] 아래 등록/수정/삭제는 관리자(ADMIN)만 호출 가능하도록 권한 체크 필요
    //  - Security/ADMIN 권한 작업 후 적용

    // 팝업 등록
    @PostMapping("/popup/insert")
    public Long insertPopup(@RequestBody PopupStoreRequest request) {
        return service.insertPopup(request); // 생성된 popId 반환
    }

    // 팝업 수정
    @PutMapping("/popup/update/{popId}")
    public void updatePopup(@PathVariable("popId") Long popId, @RequestBody PopupStoreRequest request) {
        service.updatePopup(popId, request);
    }

    // 팝업 삭제
    @DeleteMapping("/popup/delete/{popId}")
    public void deletePopup(@PathVariable("popId") Long popId) {
        service.deletePopup(popId);
    }

    // 서비스에서 IllegalArgumentException 발생 시 400 + 메시지 반환
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
package com.popq.platform.controller;

import com.popq.platform.dto.PopupStoreDTO;
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
    @GetMapping("/popup/popupList")
    public List<PopupStoreDTO> listPopup(
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

    // 팝업 상세 조회
    @GetMapping("/popup/popupDetailView/{popId}")
    public PopupStoreDTO detailViewPopup(@PathVariable("popId") Long popId) {
        return service.detailViewPopup(popId);
    }

    // 팝업 등록 : React(Axios)에서 JSON으로 보내므로 @RequestBody 필요
    @PostMapping("/popup/insert")
    public Long insertPopup(@RequestBody PopupStoreDTO dto) {
        return service.insertPopup(dto); // 생성된 popId 반환
    }

    // 팝업 수정
    @PutMapping("/popup/update")
    public void updatePopup(@RequestBody PopupStoreDTO dto) {
        service.updatePopup(dto);
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

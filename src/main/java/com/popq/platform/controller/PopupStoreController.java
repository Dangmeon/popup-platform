package com.popq.platform.controller;

import com.popq.platform.dto.ManagerResponse;
import com.popq.platform.dto.PopupManagerRequest;
import com.popq.platform.dto.PopupStoreRequest;
import com.popq.platform.dto.PopupStoreResponse;
import com.popq.platform.reservation.dto.AdminReservationResponse;
import com.popq.platform.reservation.entity.ReservationStatus;
import com.popq.platform.reservation.service.AdminReservationService;
import com.popq.platform.service.PopupStoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class PopupStoreController {

    PopupStoreService service;
    AdminReservationService adminReservationService;

    @Autowired
    public PopupStoreController(
            PopupStoreService service,
            AdminReservationService adminReservationService
    ) {
        this.service = service;
        this.adminReservationService = adminReservationService;
    }

    // 팝업 목록 조회
    // /api/popups                  : 전체
    // /api/popups?status=ONGOING   : 진행중
    // /api/popups?category=FASHION : 카테고리별
    // [회원 연동] 로그인한 회원이면 찜(favorite) 여부를 함께 반환
    @GetMapping("/api/popups")
    public List<PopupStoreResponse> listPopup(
            @AuthenticationPrincipal String username,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "sort", required = false) String sort) {
        return service.listUserPopups(username, status, category, sort);
    }

    // [회원 연동] 로그인한 회원이면 찜(favorite) 여부를 함께 반환
    // - favorite 테이블 (target_type = 'POPUP', pop_id) 조회

    // 팝업 상세 조회
    @GetMapping("/api/popups/{popId}")
    public PopupStoreResponse detailViewPopup(
            @PathVariable("popId") Long popId,
            @AuthenticationPrincipal String username) {
        return service.detailViewPopup(popId, username);
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

    // 관리자 팝업별 예약 현황 조회
    @GetMapping("/api/admin/popups/{popId}/reservations")
    public List<AdminReservationResponse> listAdminReservation(
            @PathVariable Long popId,
            @AuthenticationPrincipal String username,
            @RequestParam(value = "status", required = false)ReservationStatus status
            ){

        return adminReservationService.getReservations(username, popId, status);
    }

    // ------------------- 팝업 중지/재개/종료 메소드
    @PatchMapping("/api/admin/popups/{popId}/pause")
    public PopupStoreResponse pausePopup(@PathVariable("popId") Long popId,
                                         @AuthenticationPrincipal String username) {
        return service.pausePopup(popId, username);
    }

    // 팝업 재개
    @PatchMapping("/api/admin/popups/{popId}/resume")
    public PopupStoreResponse resumePopup(@PathVariable("popId") Long popId,
                                          @AuthenticationPrincipal String username) {
        return service.resumePopup(popId, username);
    }

    // 팝업 종료 (복구 불가)
    @PatchMapping("/api/admin/popups/{popId}/cancel")
    public PopupStoreResponse cancelPopup(@PathVariable("popId") Long popId,
                                          @AuthenticationPrincipal String username) {
        return service.cancelPopup(popId, username);
    }

    /// 테스트 결과 확인된 사항들
    /// TC-01 : 팝업 중지 >> 200 PAUSE로 잘 바뀌는거 확인 PATCH /api/admin/popups/109/pause
    /// TC-02 : 팝업 중지된 상태에서 팝업 중지 >> 409 이미 일시 중단된 팝업입니다 PATCH /api/admin/popups/109/pause
    /// TC-03 : 팝업 재개 >> 200 OPEN으로 잘 바뀌는 거 확인 PATCH /api/admin/popups/109/resume
    /// TC-04 : 이미 열린 팝업 상태에서 재개시 >> 409 일시 중단 상태인 팝업만 재개할 수 있습니다. PATCH /api/admin/popups/109/resume
    /// TC-05 : 특정 회차 취소 >> Slot Status == CANCELLED 처리 및 잔여 인원 수 0으로 돌아간 내용 확인 PATCH /api/admin/slots/10/cancel
    /// TC-06 : 이미 취소된 회차 재취소 시도 >> 이미 취소된 회차입니다. PATCH /api/admin/slots/10/cancel
    /// TC-07 : 이미 시작 시간이 지난 회차 취소 시도 >> 409 이미 시작된 회차는 취소할 수 없습니다. PATCH /api/admin/slots/11/cancel
    /// TC-08 : 팝업 영구 종료 및 연쇄 취소 실행 >> 200 CANCELLED로 변경 확인 PATCH /api/admin/popups/110/cancel
    /// TC-09 : 종료된 팝업 일반 사용자 목록 노출 제외 확인 >> ONGOING만 뜨는거 확인 GET /api/popups | GET /api/popups?status=ONGOING
    /// TC-10 : 이미 종료된 팝업 중복 종료/중단/재개 시도 >> 세가지 모두 이미 종료(취소)된 팝업입니다.
    /// TC-11 : 종료된 팝업 기본 정보 수정 차단 >> 종료된 팝업은 수정하거나 하위 회차·상품을 변경할 수 없습니다. PUT /api/admin/popups/100
    /// TC-12 : 종료된 팝업에 신규 회차 등록 차단 >> 종료된 팝업은 수정하거나 하위 회차·상품을 변경할 수 없습니다. POST /api/admin/popups/100/slots
    /// TC-13 : 종료된 팝업의 기존 회차 수정 차단 >> 종료된 팝업은 수정하거나 하위 회차·상품을 변경할 수 없습니다.
    /// TC-14 : 종료된 팝업의 상품 등록/수정/삭제 차단 >> 종료된 팝업은 수정하거나 하위 회차·상품을 변경할 수 없습니다.

}
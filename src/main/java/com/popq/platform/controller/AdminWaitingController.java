package com.popq.platform.controller;

import com.popq.platform.dto.WaitingStatusResponse;
import com.popq.platform.service.WaitingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/waitings")
public class AdminWaitingController {

    private final WaitingService waitingService;

    /** 입장 호출
     * 관리자 대기 고객 호출 및 입장 제한 타이머
     * POST /api/admin/waitings/{waitingId}/call
     */
    @PostMapping("/{waitingId}/call")
    public ResponseEntity<WaitingStatusResponse> callWaiting(@PathVariable Long waitingId) {
        WaitingStatusResponse response = waitingService.callWaiting(waitingId, 10); // 기본 10분 타이머 사용
        return ResponseEntity.ok(response);
    }
    /**  테스트 결과 (POST http://localhost:8080/api/admin/waitings/10/call)
        {
        "waitingId": 10,
        "popId": 1,
        "peopleCount": 3,
        "queueNumber": 5,
        "teamsAhead": null,
        "estimatedWaitMins": null,
        "status": "CALLED",
        "checkInCode": "CHK-0005-0010",
        "joinedAt": "2026-10-30T13:56:52.08005",
        "calledAt": "2026-11-06T11:41:31.523917"
        } **/
}
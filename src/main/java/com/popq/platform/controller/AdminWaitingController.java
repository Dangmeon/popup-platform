package com.popq.platform.controller;

import com.popq.platform.dto.CheckInRequest;
import com.popq.platform.dto.WaitingStatusResponse;
import com.popq.platform.service.WaitingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    @PostMapping("/checkIn")
    public ResponseEntity<WaitingStatusResponse> checkIn(@RequestBody @Valid CheckInRequest request) {
        WaitingStatusResponse response = waitingService.checkIn(request);
        return ResponseEntity.ok(response);
    }
    /** 테스트 결과
     * { // 만료 qr 넣을 경우
     *     "code": "CHECK_IN_EXPIRED",
     *     "errors": null,
     *     "message": "입장 가능 시간이 초과되었습니다."
     * }
     * { // 취소 qr 넣을 경우
     *     "code": "ALREADY_CANCELED",
     *     "errors": null,
     *     "message": "이미 취소된 대기입니다."
     * }
     * { // 대기 qr 넣을 경우
     *     "code": "NOT_CALLED_YET",
     *     "errors": null,
     *     "message": "호출 상태인 대기 팀만 처리할 수 있습니다."
     * }
     * { // 올바른 qr 체크인일 경우 ENTERED처리
     *     "waitingId": 21,
     *     "popId": 1,
     *     "peopleCount": 3,
     *     "queueNumber": 1,
     *     "teamsAhead": null,
     *     "estimatedWaitMins": null,
     *     "status": "ENTERED",
     *     "checkInCode": "CHK-0001-0021",
     *     "joinedAt": "2026-11-07T15:02:44.007568",
     *     "calledAt": "2026-11-07T15:05:02.121393"
     * }
     * { // 이미 입장 시
     *     "code": "ALREADY_ENTERED",
     *     "errors": null,
     *     "message": "이미 입장된 대기 입니다."
     * }
     */
}
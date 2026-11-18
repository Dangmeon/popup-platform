package com.popq.platform.waiting.controller;

import com.popq.platform.waiting.dto.AdminWaitingListResponse;
import com.popq.platform.waiting.dto.CheckInRequest;
import com.popq.platform.waiting.dto.WaitingStatusResponse;
import com.popq.platform.waiting.entity.WaitingStatus;
import com.popq.platform.waiting.service.WaitingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminWaitingController {

    private final WaitingService waitingService;

    /** 입장 호출
     * 관리자 대기 고객 호출 및 입장 제한 타이머
     * POST /api/admin/waitings/{waitingId}/call
     */
    @PostMapping("/waitings/{waitingId}/call")
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

    /** 체크인
     * 관리자 호출 고객 입장 처리
     * POST /api/admin/waitings/checkIn
     */
    @PostMapping("/waitings/checkIn")
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

    /** 관리자용 대기 고객 관리
     * POST /api/admin/popups/{popId}/waitings
     */
    @GetMapping("/popups/{popId}/waitings")
    public ResponseEntity<AdminWaitingListResponse> getAdminWaitingList(
            @PathVariable Long popId,
            @RequestParam(required = false) WaitingStatus status
    ) {
        AdminWaitingListResponse response = waitingService.getAdminWaitingList(popId, status);
        return ResponseEntity.ok(response);
    }
    /** 테스트 결과
     * {
     *     "popId": 1,
     *     "totalWaitingTeams": 0,
     *     "totalCalledTeams": 0,
     *     "waitings": [
     *         {
     *             "waitingId": 23,
     *             "popId": 1,
     *             "peopleCount": 3,
     *             "queueNumber": 1,
     *             "teamsAhead": null,
     *             "estimatedWaitMins": null,
     *             "status": "ENTERED",
     *             "checkInCode": "CHK-0001-0023",
     *             "joinedAt": "2026-11-10T14:43:47.71617",
     *             "calledAt": "2026-11-10T14:50:42.93443"
     *         },
     *         {
     *             "waitingId": 24,
     *             "popId": 1,
     *             "peopleCount": 1,
     *             "queueNumber": 2,
     *             "teamsAhead": null,
     *             "estimatedWaitMins": null,
     *             "status": "ENTERED",
     *             "checkInCode": "CHK-0002-0024",
     *             "joinedAt": "2026-11-10T14:44:18.384282",
     *             "calledAt": "2026-11-10T15:14:34.040379"
     *         },
     *         {
     *             "waitingId": 25,
     *             "popId": 1,
     *             "peopleCount": 1,
     *             "queueNumber": 3,
     *             "teamsAhead": null,
     *             "estimatedWaitMins": null,
     *             "status": "CANCELLED",
     *             "checkInCode": "CHK-0003-0025",
     *             "joinedAt": "2026-11-10T14:50:26.022399",
     *             "calledAt": null
     *         },
     *         {
     *             "waitingId": 26,
     *             "popId": 1,
     *             "peopleCount": 1,
     *             "queueNumber": 4,
     *             "teamsAhead": null,
     *             "estimatedWaitMins": null,
     *             "status": "CANCELLED",
     *             "checkInCode": "CHK-0004-0026",
     *             "joinedAt": "2026-11-10T15:14:15.712819",
     *             "calledAt": null
     *         }
     *     ]
     * }
     */
}
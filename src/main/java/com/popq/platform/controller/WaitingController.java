package com.popq.platform.controller;

import com.popq.platform.dto.WaitingRegisterRequest;
import com.popq.platform.dto.WaitingStatusResponse;
import com.popq.platform.service.WaitingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 현장 대기열 등록 API
 * POST /api/waitings
 * param request 팝업스토어 ID, 전화번호, 인원수 필요!
 * return 201 Created & 등록된 대기열 상세 정보 (순번, 앞 대기 팀 수 등)
 * WaitingService에서 findById(request.getPopId())와 walkin_status == 'OPEN'를 검증하기 때문에
 * 팝업 스토어 테이블에 더미 데이터 한개 이상 필요
 */

@RestController
@RequestMapping("/api/waitings") // 기본 매핑 경로 등록
@RequiredArgsConstructor
public class WaitingController {

    private final WaitingService waitingService;

    @PostMapping
    public ResponseEntity<WaitingStatusResponse> registerWaiting(
            @Valid @RequestBody WaitingRegisterRequest request // WaitingRegisterRequest의 조건 검증 미충족시 400 bad request
    ) {
        WaitingStatusResponse response = waitingService.registerWaiting(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
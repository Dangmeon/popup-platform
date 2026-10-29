package com.popq.platform.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WaitingStatus {
    WAITING("대기 중"),
    CALLED("입장 호출"),
    ENTERED("입장 완료"),
    CANCELLED("대기 취소"),
    EXPIRED("시간 초과 만료");

    private final String description;
}

// ============================================================
// WaitingStatus : 대기열 상태관리 파일입니다.
// WAITING : 순서 대기 중인 상태
// CALLED : 차례가 되어 입장 호출된 상태
// ENTERED : 호출된 손님이 입장 완료된 상태
// CANCELLED : 손님이 대기를 취소한 상태
// EXPIRED : 호출된 손님이 일정 시간동안 입장하지 않아 만료된 상태
// ============================================================
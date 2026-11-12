package com.popq.platform.waiting.event;

public record WaitingRegisteredEvent(
        Long waitingId, String phone,
        String popupName, Integer queueNumber,
        int teamsAhead) { // 대기 등록 알림에 필요한 params
}
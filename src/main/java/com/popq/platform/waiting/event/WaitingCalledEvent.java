package com.popq.platform.waiting.event;

public record WaitingCalledEvent(
        Long waitingId, String phone,
        String popupName, Integer queueNumber,
        String checkInCode) { // 호출 알림시 필요한 params
}
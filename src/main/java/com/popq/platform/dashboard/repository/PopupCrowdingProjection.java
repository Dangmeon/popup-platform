package com.popq.platform.dashboard.repository;

public interface PopupCrowdingProjection {
    Long getPopId();
    String getTitle();
    Long getCapacity();   // 오늘 회차 정원 합계
    Long getReserved();   // 오늘 회차 예약 인원 합계
}

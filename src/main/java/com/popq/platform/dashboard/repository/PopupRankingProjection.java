package com.popq.platform.dashboard.repository;

import java.time.LocalDate;

public interface PopupRankingProjection {
    Long getPopId();
    String getTitle();
    String getLocation();
    LocalDate getStartDate();
    LocalDate getEndDate();
    String getOpStatus();
    Long getReservedPeople();   // 예약 인원 합계
    Long getReservationCount(); // 예약 건수
}

package com.popq.platform.repository;

import java.time.LocalDate;

public interface DailyReservationProjection {
    LocalDate getSlotDate();
    Long getReserved();   // 예약 수
    Long getEntered();    // 입장(USED) 수
}

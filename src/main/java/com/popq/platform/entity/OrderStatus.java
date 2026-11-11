package com.popq.platform.entity;

public enum OrderStatus {
    PENDING,   // 결제 대기
    PAID,      // 결제 완료
    CANCELLED,  // 주문 취소
    EXPIRED,   // 결제 시간 초과
    FAILED     // 결제 실패
}

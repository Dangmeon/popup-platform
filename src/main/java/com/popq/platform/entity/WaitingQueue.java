package com.popq.platform.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "waiting_queue")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WaitingQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "waiting_id") // DB의 waiting_id를
    private Long waitingId; // 코드에선 waitingId 이렇게 쓰겠다는 뜻

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pop_id", nullable = false)
    private PopupStore popupStore;

    @Column(name = "phone", nullable = false, length = 20) // NOT NULL VARCHAR2(20)
    private String phone;

    @Column(name = "people_count", nullable = false) // 인원 수 NOT NULL
    private Integer peopleCount;

    @Column(name = "queue_number") // 대기 순번
    private Integer queueNumber;

    @Enumerated(EnumType.STRING) // entity.WaitingStatus
    @Column(name = "waiting_status", nullable = false, length = 20) // 대기 상태 NOT NULL VARCHAR2(20)
    private WaitingStatus waitingStatus;

    @Column(name = "joined_at", nullable = false, updatable = false) // 대기열 등록 시간 NOT NULL READONLY
    private LocalDateTime joinedAt;

    @Column(name = "called_at") // 호출 시간
    private LocalDateTime calledAt;

    @Column(name = "expires_at") // 만료 시간
    private LocalDateTime expiresAt;

    @Builder
    public WaitingQueue(PopupStore popupStore, String phone, int peopleCount, Integer queueNumber) {
        this.popupStore = popupStore;
        this.phone = phone; // 연락처
        this.peopleCount = peopleCount; // 인원수
        this.queueNumber = queueNumber; // 대기 번호
        this.waitingStatus = WaitingStatus.WAITING; // 기본값 'WAITING'
        this.joinedAt = LocalDateTime.now(); // 대기 등록 = 현재 시간
    }

    public void call(int timeoutMinutes) {
        this.waitingStatus = WaitingStatus.CALLED; // 상태 'CALLED'
        this.calledAt = LocalDateTime.now(); // 호출 = 현재 시간
        this.expiresAt = this.calledAt.plusMinutes(timeoutMinutes);
    } // 호출 될 때

    public void enter() {
        this.waitingStatus = WaitingStatus.ENTERED; // 상태 'ENTERED'
    } // 입장 할 때

    public void cancel() {
        if (this.waitingStatus == WaitingStatus.ENTERED) {
            throw new IllegalStateException("이미 입장 완료된 대기는 취소할 수 없습니다.");
        }
        if (this.waitingStatus == WaitingStatus.CANCELLED) {
            throw new IllegalStateException("이미 취소된 대기입니다.");
        }
        if (this.waitingStatus == WaitingStatus.EXPIRED) {
            throw new IllegalStateException("이미 만료 처리된 대기입니다.");
        }

        this.waitingStatus = WaitingStatus.CANCELLED;
    } // 취소 될 때

    public void expire() {
        this.waitingStatus = WaitingStatus.EXPIRED; // 상태 'EXPIRED'
    } // 만료 될 때
}

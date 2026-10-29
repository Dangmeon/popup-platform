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
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "waiting_queue_seq_gen") // 시퀀스 매핑
    @SequenceGenerator(
            name = "waiting_queue_seq_gen",
            sequenceName = "WAITING_QUEUE_SEQ",
            allocationSize = 1
    )
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

    @Transient // 매핑 x 임시
    private LocalDateTime enteredAt; //  입장 시간(평균 대기 시간 계산 시 필요)

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
    } // 등록 될 때

    public void call(int timeoutMinutes) {
        if (this.waitingStatus != WaitingStatus.WAITING) { // WAITING 상태가 아니라면 예외 던지기
            throw new IllegalStateException("대기 상태인 대기열만 호출할 수 있습니다. 현재 상태: " + this.waitingStatus);
        } //

        LocalDateTime now = LocalDateTime.now(); // 현재 시간 객체 생성
        this.waitingStatus = WaitingStatus.CALLED; // called로 변경
        this.calledAt = now; // 호출 시각 = 현재 시각
        this.expiresAt = now.plusMinutes(timeoutMinutes); // 현재 시각 + 타임아웃 시간
    } // 호출 될 때 (WAITING -> CALLED)


    public void enter() {
        validateEnterable();  // 입장 확인용 메소드
        this.waitingStatus = WaitingStatus.ENTERED; // 상태 'ENTERED'
        this.enteredAt = LocalDateTime.now(); // 보류 : 칼럼이 아직 DB에 없음 추가 시 @Transient만 지우고 반영하면 됨
    } // 입장 할 때 (CALLED -> ENTERED)

    private void validateEnterable() { // 검증 (-> ENTERED)
        if (this.waitingStatus == WaitingStatus.ENTERED) { // 완료된
            throw new IllegalStateException("이미 입장 완료된 대기열입니다.");
        }
        if (this.waitingStatus == WaitingStatus.CANCELLED) { // 취소된
            throw new IllegalStateException("취소된 대기열은 입장 처리할 수 없습니다.");
        }
        if (this.waitingStatus == WaitingStatus.EXPIRED) { // 만료된
            throw new IllegalStateException("호출 시간이 만료된 대기열은 입장 처리할 수 없습니다.");
        }
    }

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
        if (this.waitingStatus != WaitingStatus.CALLED) { // 상태 'CALLED'
            throw new IllegalStateException("호출 상태인 대기열만 만료 처리할 수 있습니다. 현재 상태: " + this.waitingStatus);
        }

        this.waitingStatus = WaitingStatus.EXPIRED;
    } // 만료 될 때 (CALLED -> EXPIRED)
}

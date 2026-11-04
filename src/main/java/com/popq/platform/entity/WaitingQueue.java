package com.popq.platform.entity;

import com.popq.platform.exception.WaitingErrorCode;
import com.popq.platform.exception.WaitingException;
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
        if (this.waitingStatus == WaitingStatus.CALLED) { // CALLED 상태인데 또 호출한다면 예외던지기
            throw new WaitingException(WaitingErrorCode.ALREADY_CALLED); // 400 이미 호출된 대기입니다.
        }
        if (this.waitingStatus != WaitingStatus.WAITING) { // WAITING 상태가 아니라면 예외 던지기
            throw new WaitingException(WaitingErrorCode.INVALID_STATUS_FOR_CALL); // 400 대기 중 상태일 때만 호출할 수 있습니다.
        }

        LocalDateTime now = LocalDateTime.now(); // 현재 시간 객체 생성
        this.waitingStatus = WaitingStatus.CALLED; // called로 변경
        this.calledAt = now; // 호출 시각 = 현재 시각
        this.expiresAt = now.plusMinutes(timeoutMinutes); // 현재 시각 + 타임아웃 시간
    } // 호출 될 때 (WAITING -> CALLED)


    public void enter() {
        validateNotTerminated();

        if (this.waitingStatus != WaitingStatus.CALLED) { // 호출 상태일때만 입장 가능
            throw new WaitingException(WaitingErrorCode.NOT_CALLED_YET);
        }
        this.waitingStatus = WaitingStatus.ENTERED; // 상태 'ENTERED'
        this.enteredAt = LocalDateTime.now(); // 보류 : 칼럼이 아직 DB에 없음 추가 시 @Transient만 지우고 반영하면 됨
    } // 입장 할 때 (CALLED -> ENTERED)

    // 공통 검증 메서드
    private void validateNotTerminated() {
        if (this.waitingStatus == WaitingStatus.ENTERED) { // 이미 입장 상태인 경우
            throw new WaitingException(WaitingErrorCode.ALREADY_ENTERED);
        }
        if (this.waitingStatus == WaitingStatus.CANCELLED) { // 이미 취소 상태인 경우
            throw new WaitingException(WaitingErrorCode.ALREADY_CANCELED);
        }
        if (this.waitingStatus == WaitingStatus.EXPIRED) { // 이미 만료 상태인 경우
            throw new WaitingException(WaitingErrorCode.ALREADY_EXPIRED);
        }
    }

    public void cancel() {
        validateNotTerminated();
        this.waitingStatus = WaitingStatus.CANCELLED;
    }

    public void expire() {
        if (this.waitingStatus != WaitingStatus.CALLED) { // 상태 'CALLED'
            throw new WaitingException(WaitingErrorCode.NOT_CALLED_YET);
        }
        this.waitingStatus = WaitingStatus.EXPIRED;
    } // 만료 될 때 (CALLED -> EXPIRED)
}

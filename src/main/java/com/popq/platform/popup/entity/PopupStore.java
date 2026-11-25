package com.popq.platform.popup.entity;

import com.popq.platform.auth.entity.User;
import com.popq.platform.popup.dto.PopupStoreRequest;
import com.popq.platform.popup.exception.PopupErrorCode;
import com.popq.platform.popup.exception.PopupException;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table (name = "popup_store")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PopupStore {
    @Id
    @SequenceGenerator(name = "popup_store_seq_gen", sequenceName = "popup_store_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "popup_store_seq_gen")

    @Column(name = "pop_id")
    private Long popId;

    @Column(name = "title")
    private String title;

    @Column(name = "location")
    private String location;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "category")
    private String category;

    @Column(name = "description")
    private String description;

    @Column(name = "store_img")
    private String storeImg;

    @Column(name = "round_num")
    private Integer roundNum;

    @Column(name = "reserv_start")
    private String reservStart;

    @Column(name = "reserv_end")
    private String reservEnd;

    @Column(name = "reserv_people_cap")
    private Integer reservPeopleCap;

    // 운영 상태 : OPEN 정상 운영 / PAUSED 일시 중단 / CANCELLED 종료(복구 불가)
    // 등록 시 항상 OPEN, 등록·수정 요청으로는 바꿀 수 없음 (상태 변경 API에서만 변경)
    @Column(name = "op_status")
    @Builder.Default
    private String opStatus = "OPEN";

    // 담당 매니저 (popup_store.manager_id -> member.member_id FK, 없으면 null)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private User manager;

    // Request -> Entity 변환시 사용
    // 등록할 때만 사용 : popId = null (시퀀스가 번호 생성) / 수정은 아래 update() 사용
    public static PopupStore toEntity(Long popId, PopupStoreRequest request) {
        return PopupStore.builder()
            .popId(popId)
            .title(request.getTitle())
            .location(request.getLocation())
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .category(request.getCategory() != null ? request.getCategory() : "ETC")
            .description(request.getDescription())
            .storeImg(request.getStoreImg())
            .roundNum(request.getRoundNum())
            .reservStart(request.getReservStart())
            .reservEnd(request.getReservEnd())
            .reservPeopleCap(request.getReservPeopleCap())
            .build();
    }

    // 팝업 수정 : DB에서 조회한 객체의 값을 바꾸면 트랜잭션이 끝날 때 JPA가 변경을 감지해서 자동으로 UPDATE
    public void update(PopupStoreRequest request) {
        this.title = request.getTitle();
        this.location = request.getLocation();
        this.startDate = request.getStartDate();
        this.endDate = request.getEndDate();
        this.description = request.getDescription();
        this.storeImg = request.getStoreImg();
        this.roundNum = request.getRoundNum();
        this.reservStart = request.getReservStart();
        this.reservEnd = request.getReservEnd();
        this.reservPeopleCap = request.getReservPeopleCap();
        if (request.getCategory() != null) { // 비우면 기존 값 유지
            this.category = request.getCategory();
        }
    }

    // 담당 매니저 지정 (MANAGER가 팝업을 등록하면 본인으로 지정)
    public void assignManager(User manager) {
        this.manager = manager;
    }

    // 팀장 작성 --------- 이하 (오픈/중지/취소) 메서드

    public void pause() { // 중지 할 때
        if ("CANCELLED".equals(this.opStatus)) {
            throw new PopupException(PopupErrorCode.POPUP_ALREADY_CANCELLED); // 팝업이 이미 취소/닫혀 있으면 예외
        }
        if ("PAUSED".equals(this.opStatus)) {
            throw new PopupException(PopupErrorCode.POPUP_ALREADY_PAUSED); // 팝업이 이미 중지된 상태면 예외
        }
        this.opStatus = "PAUSED"; // 나머지 상태일 경우에만 중지 상태로 변경
    }

    public void resume() { // 재개 할때
        if ("CANCELLED".equals(this.opStatus)) {
            throw new PopupException(PopupErrorCode.POPUP_ALREADY_CANCELLED);  // 팝업이 이미 취소/닫혀 있으면 예외
        }
        if (!"PAUSED".equals(this.opStatus)) {
            throw new PopupException(PopupErrorCode.POPUP_NOT_PAUSED);  // 팝업이 중지된 상태가 아니라면 예외
        }
        this.opStatus = "OPEN"; // 다시 오픈 상태로 변경
    }

    public void cancel() { // 캔슬 취소 시킬 때
        if ("CANCELLED".equals(this.opStatus)) {
            throw new PopupException(PopupErrorCode.POPUP_ALREADY_CANCELLED); // 이미 취소 된 경우 예외
        }
        this.opStatus = "CANCELLED"; // 취소 상태로 변경 (못 되돌림)
    }

    public void validateNotCancelled() { // 취소된 팝업(굿즈/)은 수정 불가
        if ("CANCELLED".equals(this.opStatus)) {
            throw new PopupException(PopupErrorCode.POPUP_CANCELLED_MODIFICATION_DENIED);
        }
    }
}

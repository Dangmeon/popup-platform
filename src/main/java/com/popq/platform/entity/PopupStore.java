package com.popq.platform.entity;

import com.popq.platform.dto.PopupStoreRequest;
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

    @Column(name = "walkin_status")
    private String walkinStatus;

    // Request -> Entity 변환시 사용
    // 등록 : popId = null (시퀀스가 번호 생성) / 수정 : popId = 수정할 팝업 번호
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
            .walkinStatus(request.getWalkinStatus() != null ? request.getWalkinStatus() : "OPEN")
            .build();
    }
}

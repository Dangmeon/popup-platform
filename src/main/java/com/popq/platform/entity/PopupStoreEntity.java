package com.popq.platform.entity;

import com.popq.platform.dto.PopupStoreDTO;
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
public class PopupStoreEntity {
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

    public static PopupStoreEntity toEntity(PopupStoreDTO dto) {
        return PopupStoreEntity.builder()
            .popId(dto.getPopId())
            .title(dto.getTitle())
            .location(dto.getLocation())
            .startDate(dto.getStartDate())
            .endDate(dto.getEndDate())
            .category(dto.getCategory() != null ? dto.getCategory() : "ETC")
            .description(dto.getDescription())
            .storeImg(dto.getStoreImg())
            .roundNum(dto.getRoundNum())
            .reservStart(dto.getReservStart())
            .reservEnd(dto.getReservEnd())
            .reservPeopleCap(dto.getReservPeopleCap())
            .walkinStatus(dto.getWalkinStatus() != null ? dto.getWalkinStatus() : "OPEN")
            .build();
    }
}

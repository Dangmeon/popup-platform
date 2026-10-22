package com.popq.platform.dto;

import com.popq.platform.entity.PopupStoreEntity;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PopupStoreDTO {
    private Long popId;
    private String title;
    private String location;
    private LocalDate startDate;
    private LocalDate endDate;
    private String category;
    private String description;
    private String storeImg;
    private Integer roundNum;
    private String reservStart;
    private String reservEnd;
    private Integer reservPeopleCap;
    private String walkinStatus;

    public static PopupStoreDTO toDto(PopupStoreEntity entity) {
        return PopupStoreDTO.builder()
            .popId(entity.getPopId())
            .title(entity.getTitle())
            .location(entity.getLocation())
            .startDate(entity.getStartDate())
            .endDate(entity.getEndDate())
            .category(entity.getCategory())
            .description(entity.getDescription())
            .storeImg(entity.getStoreImg())
            .roundNum(entity.getRoundNum())
            .reservStart(entity.getReservStart())
            .reservEnd(entity.getReservEnd())
            .reservPeopleCap(entity.getReservPeopleCap())
            .walkinStatus(entity.getWalkinStatus())
            .build();
    }
}

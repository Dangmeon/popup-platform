package com.popq.platform.dto;

import com.popq.platform.entity.PopupStore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
@Builder
public class PopupStoreResponse {
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
    private String status; // UPCOMING 오픈 예정 / ONGOING 진행중 / ENDED 종료 (계산값)

    // [회원 연동] 로그인 회원의 찜 여부

    // Entity -> Response 변환시 사용
    public static PopupStoreResponse toDto(PopupStore entity) {
        return PopupStoreResponse.builder()
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
                .status(getStatus(entity))
                .build();
    }

    // 오늘 날짜 기준 팝업 진행 상태 계산
    private static String getStatus(PopupStore entity) {
        LocalDate today = LocalDate.now();
        if (today.isBefore(entity.getStartDate())) {
            return "UPCOMING";
        }
        if (today.isAfter(entity.getEndDate())) {
            return "ENDED";
        }
        return "ONGOING";
    }
}

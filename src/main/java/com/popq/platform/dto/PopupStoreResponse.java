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
    private String opStatus;  // (OPEN / PAUSED / CANCELLED)
    private Long managerId;   // 담당 매니저 회원 번호 (없으면 null)
    private String status; // UPCOMING 오픈 예정 / ONGOING 진행중 / ENDED 종료 (계산값)

    // 로그인 회원의 찜 여부 / 찜 개수 카운트 변수
    private Boolean isFavorite;
    private Long favoriteCount;

    public static PopupStoreResponse toDto(PopupStore entity) {
        return toDto(entity, false, 0L);
    }

    // 사용자 목록/상세 조회용 (찜 여부 + 찜 개수 포함)
    public static PopupStoreResponse toDto(PopupStore entity, boolean isFavorite, long favoriteCount) {
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
                .opStatus(entity.getOpStatus())
                .managerId(entity.getManager() != null ? entity.getManager().getId() : null)
                .status(getStatus(entity))
                .isFavorite(isFavorite)
                .favoriteCount(favoriteCount)
                .build();
    }

    // 오늘 날짜 기준 팝업 진행 상태 계산
    private static String getStatus(PopupStore entity) {
        if (entity.getStartDate() == null || entity.getEndDate() == null) {
            return null; // 운영기간이 비어 있으면 상태를 계산하지 않음
        }
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

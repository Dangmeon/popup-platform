package com.popq.platform.dto;

import com.popq.platform.repository.PopupRankingProjection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
@Builder
public class PopupRankingResponse { // 예약 많은 순 팝업 목록 응답
    private int rank;               // 순위 (1부터)
    private Long popId;
    private String title;
    private String location;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;          // UPCOMING 오픈 예정 / ONGOING 진행중 / ENDED 종료 (계산값)
    private String opStatus;        // OPEN / PAUSED / CANCELLED
    private long reservedPeople;    // 예약 인원 합계 (CONFIRMED, USED)
    private long reservationCount;  // 예약 건수 (CONFIRMED, USED)

    public static PopupRankingResponse toDto(int rank, PopupRankingProjection row) {
        return PopupRankingResponse.builder()
                .rank(rank)
                .popId(row.getPopId())
                .title(row.getTitle())
                .location(row.getLocation())
                .startDate(row.getStartDate())
                .endDate(row.getEndDate())
                .status(getStatus(row.getStartDate(), row.getEndDate()))
                .opStatus(row.getOpStatus())
                .reservedPeople(row.getReservedPeople() == null ? 0 : row.getReservedPeople())
                .reservationCount(row.getReservationCount() == null ? 0 : row.getReservationCount())
                .build();
    }

    // 진행 상태 계산 (PopupStoreResponse와 같은 기준)
    private static String getStatus(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            return null;
        }
        LocalDate today = LocalDate.now();
        if (today.isBefore(startDate)) {
            return "UPCOMING";
        }
        if (today.isAfter(endDate)) {
            return "ENDED";
        }
        return "ONGOING";
    }
}

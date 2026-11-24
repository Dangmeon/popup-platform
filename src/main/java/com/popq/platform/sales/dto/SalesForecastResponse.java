package com.popq.platform.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesForecastResponse {

    private Long popId;
    private String popTitle;

    // 집계 기준 단위 ("MONTH": 플랫폼 전체 월단위 / "WEEK": 개별 팝업 주단위)
    private String periodUnit;

    // [1단계: DB + Java 정량 실적 지표 (원 단위)]
    // - 전체(MONTH)일 때는 전월 / 당월 기준, 개별 팝업(WEEK)일 때는 전주 / 금주 기준 값이 들어간다.
    private long lastPeriodSales;          // 저번 달(또는 저번 주) 확정 매출
    private long currentPeriodSales;       // 이번 달(또는 이번 주) 오늘까지 확정 매출
    private long estimatedCurrentSales;    // 이번 달(또는 이번 주) 말 예상 총 매출 (일평균 환산)
    private double growthRatePercent;      // 직전 기간 대비 이번 기간 예상 증감률 (%)

    // [2단계: LLM 예측 시나리오 (원 단위)]
    private String targetNextPeriod;       // 예측 대상 기간 (예: "2026-12" / "2026-11-30 ~ 2026-12-06")
    private long expectedNextSales;        // 다음 달(또는 다음 주) 기본 예상 매출
    private long conservativeSales;        // 보수적 예상 매출
    private long optimisticSales;          // 낙관적 예상 매출

    // [3단계: LLM 운영 인사이트 및 메타데이터]
    private String trendSummary;
    private List<String> actionItems;
    private boolean fallback;
    private LocalDateTime generatedAt;
}
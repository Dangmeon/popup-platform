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

    // 조회 범위 식별자 (플랫폼 전체 예측이면 popId = null, 개별 팝업 예측이면 해당 팝업 ID 집어넣기)
    private Long popId;
    private String popTitle;

    // [1단계: DB + Java가 100% 정확하게 계산한 정량 실적 지표 (원 단위)]
    // -> 숫자 계산을 LLM에 맡기지 않고 백엔드에서 확정해 환각(산수 오류)을 차단한다.
    private long lastMonthSales;          // 저번 달 확정 매출
    private long currentMonthSales;       // 이번 달 오늘까지 확정 매출
    private long estimatedCurrentSales;   // 이번 달 말 예상 매출 (일평균 매출 × 이번 달 총 일수)
    private double growthRatePercent;     // 전월 대비 이번 달 예상 증감률 (%)

    // [2단계: LLM(Llama 3)이 추세와 운영 기간을 해석해 도출한 다음 달 예측 시나리오 (원 단위)]
    private String targetNextMonth;       // 예측 대상 연월 (예: "2026-12")
    private long expectedNextMonthSales;  // 기본(중립) 예상 매출
    private long conservativeSales;       // 보수적 예상 매출 (하방 리스크 반영)
    private long optimisticSales;         // 낙관적 예상 매출 (프로모션/수요 증가 반영)

    // [3단계: LLM이 생성한 운영 인사이트 및 시스템 메타데이터]
    private String trendSummary;          // 매출 추세 한 줄 요약
    private List<String> actionItems;     // 다음 달 매출 증대를 위한 실행 제안 (2~3개)
    private boolean fallback;             // true면 Groq API 장애/키 미설정으로 Java 통계 폴백이 동작했음을 표시
    private LocalDateTime generatedAt;    // 예측 결과가 생성된 시각 (캐싱 시 최초 생성 시각 유지)
}
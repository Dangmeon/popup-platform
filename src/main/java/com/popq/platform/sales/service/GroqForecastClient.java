package com.popq.platform.sales.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GroqForecastClient {

    private final ObjectMapper objectMapper;

    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String apiUrl;

    @Value("${groq.api.key:}")
    private String apiKey;

    @Value("${groq.api.model:openai/gpt-oss-120b}")
    private String model;

    public record LlmForecastResult(
            long expectedNextSales,
            long conservativeSales,
            long optimisticSales,
            String trendSummary,
            List<String> actionItems,
            boolean fallback
    ) {}

    /**
     * 월 단위 또는 주 단위 매출 지표를 Groq LLM에 전달해 다음 기간 예상 매출과 전략 받기
     */
    public LlmForecastResult requestSalesForecast(
            String periodUnit, // "MONTH" 또는 "WEEK"
            String scopeTitle,
            String operationPeriodNote,
            String nextPeriodLabel,
            long lastPeriodSales,
            long lastPeriodOrders,
            long currentPeriodSales,
            long currentPeriodOrders,
            long estimatedCurrentSales,
            double growthRatePercent
    ) {
        String unitKor = "WEEK".equals(periodUnit) ? "주" : "달";

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GROQ_API_KEY가 비어 있어 통계 기반 폴백 결과를 반환합니다.");
            return buildFallbackResult(unitKor, lastPeriodSales, estimatedCurrentSales, growthRatePercent);
        }

        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(3000);
            factory.setReadTimeout(10000);
            RestClient restClient = RestClient.builder().requestFactory(factory).build();

            String systemPrompt = String.format("""
                당신은 팝업스토어 플랫폼의 이커머스 매출 데이터 분석가입니다.
                제공된 직전 %s 실적, 이번 %s 추세, 팝업 운영 기간 정보를 분석해 다음 %s 예상 매출(원화 정수)과 운영 전략을 반드시 아래 JSON 형식으로만 응답하세요.
                개별 팝업스토어가 다음 %s 중 종료되거나 이미 종료되었다면, 실제 영업 가능 일수를 반영해 현실적으로 예측하세요.
                마크다운 코드블록(```)이나 부가 설명 문장은 절대 포함하지 마세요.
                {
                  "expectedNextSales": 0,
                  "conservativeSales": 0,
                  "optimisticSales": 0,
                  "trendSummary": "직전 %s 대비 이번 %s 추세 및 다음 %s 전망 한 줄 요약 (한국어)",
                  "actionItems": [
                    "실행 가능한 매출 증대 전략 1 (한국어)",
                    "실행 가능한 매출 증대 전략 2 (한국어)"
                  ]
                }
                """, unitKor, unitKor, unitKor, unitKor, unitKor, unitKor, unitKor);

            String userPrompt = String.format("""
                - 분석 단위: %s단위 예측
                - 분석 대상: %s
                - 운영 기간 정보: %s
                - 예측 대상 기간: %s
                - 저번 %s 확정 매출: %d원 (주문 %d건)
                - 이번 %s 현재까지 확정 매출: %d원 (주문 %d건)
                - 이번 %s 말 예상 총 매출(일평균 환산): %d원
                - 직전 %s 대비 이번 %s 예상 증감률: %.1f%%
                
                위 수치를 근거로 %s의 기본 예상 매출(expectedNextSales), 보수적 예상 매출(conservativeSales), 낙관적 예상 매출(optimisticSales)을 원 단위로 산출하고 분석 요약과 액션 아이템 2~3개를 작성해 주세요.
                """,
                    unitKor, scopeTitle, operationPeriodNote, nextPeriodLabel,
                    unitKor, lastPeriodSales, lastPeriodOrders,
                    unitKor, currentPeriodSales, currentPeriodOrders,
                    unitKor, estimatedCurrentSales,
                    unitKor, unitKor, growthRatePercent,
                    nextPeriodLabel
            );

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "temperature", 0.3,
                    "max_tokens", 2048,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    )
            );

            String rawResponse = restClient.post()
                    .uri(apiUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(rawResponse);
            String content = root.path("choices").get(0).path("message").path("content").asString();
            JsonNode forecastJson = objectMapper.readTree(content);

            long expected = forecastJson.path("expectedNextSales").asLong(estimatedCurrentSales);
            long conservative = forecastJson.path("conservativeSales").asLong(Math.round(expected * 0.88));
            long optimistic = forecastJson.path("optimisticSales").asLong(Math.round(expected * 1.12));
            String summary = forecastJson.path("trendSummary")
                    .asString("직전 " + unitKor + " 및 이번 " + unitKor + " 판매 추세를 반영한 다음 " + unitKor + " 예상 매출입니다.");

            List<String> actions = new ArrayList<>();
            if (forecastJson.has("actionItems") && forecastJson.get("actionItems").isArray()) {
                for (JsonNode item : forecastJson.get("actionItems")) {
                    actions.add(item.asString());
                }
            }
            if (actions.isEmpty()) {
                actions.add("인기 굿즈 품절 방지를 위한 선제적 재고 확보");
                actions.add("관심(찜) 등록 사용자 대상 한정판 굿즈 알림 프로모션 진행");
            }

            return new LlmForecastResult(expected, conservative, optimistic, summary, actions, false);

        } catch (Exception e) {
            log.error("Groq LLM 호출 실패 - 폴백 결과 반환: {}", e.getMessage());
            return buildFallbackResult(unitKor, lastPeriodSales, estimatedCurrentSales, growthRatePercent);
        }
    }

    private LlmForecastResult buildFallbackResult(String unitKor, long lastPeriodSales, long estimatedCurrentSales, double growthRatePercent) {
        long base = estimatedCurrentSales > 0 ? estimatedCurrentSales : lastPeriodSales;
        double clampedRate = Math.clamp(growthRatePercent / 100.0, -0.2, 0.3);
        long expected = Math.max(0L, Math.round(base * (1.0 + clampedRate * 0.5)));

        return new LlmForecastResult(
                expected,
                Math.round(expected * 0.85),
                Math.round(expected * 1.15),
                "직전 " + unitKor + " 실적과 이번 " + unitKor + " 일평균 매출 추세를 반영해 산출된 기본 예측치입니다.",
                List.of(
                        "이번 " + unitKor + " 판매 상위 굿즈 중심의 번들 상품 구성 검토",
                        "팝업 종료 임박 시점 한정 수량 프로모션 운영"
                ),
                true
        );
    }
}
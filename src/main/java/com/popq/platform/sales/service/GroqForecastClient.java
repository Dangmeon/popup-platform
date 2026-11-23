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

    // application.properties 또는 환경변수(GROQ_API_KEY)에서 설정값을 주입받는다.
    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String apiUrl;

    @Value("${groq.api.key:}")
    private String apiKey;

    @Value("${groq.api.model:openai/gpt-oss-120b}")
    private String model;

    // 서비스 계층으로 전달할 LLM 응답 파싱 결과 객체
    public record LlmForecastResult(
            long expectedNextMonthSales,
            long conservativeSales,
            long optimisticSales,
            String trendSummary,
            List<String> actionItems,
            boolean fallback
    ) {}

    /**
     * Java가 집계한 매출 지표를 Groq Cloud(Llama 3) API로 전송해 다음 달 예측치와 전략을 받아온다.
     * - 외부 API 호출 실패, 네트워크 지연, JSON 파싱 오류 시 예외를 밖으로 던지지 않고
     *   buildFallbackResult()를 호출하여 대시보드 장애를 방지한다.
     */
    public LlmForecastResult requestNextMonthForecast(
            String scopeTitle,
            String operationPeriodNote,
            String nextMonthLabel,
            long lastMonthSales,
            long lastMonthOrders,
            long currentMonthSales,
            long currentMonthOrders,
            long estimatedCurrentSales,
            double growthRatePercent
    ) {
        // 1. API 키가 환경변수에 설정되지 않은 개발 환경에서도 서버가 죽지 않고 통계 폴백으로 동작하게 방어
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GROQ_API_KEY가 비어 있어 통계 기반 폴백 결과를 반환합니다.");
            return buildFallbackResult(lastMonthSales, estimatedCurrentSales, growthRatePercent);
        }

        try {
            // 2. 외부 API 응답 지연으로 스레드가 무한 대기하는 것을 막기 위해 타임아웃 명시
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(3000); // 서버 연결 제한 3초
            factory.setReadTimeout(10000);   // LLM 생성 응답 대기 제한 10초
            RestClient restClient = RestClient.builder().requestFactory(factory).build();

            // 3. 시스템 프롬프트: LLM의 역할, 출력 JSON 스키마, 팝업 종료일 반영 규칙을 강제한다.
            String systemPrompt = """
                당신은 팝업스토어 플랫폼의 이커머스 매출 데이터 분석가입니다.
                제공된 전월 실적, 당월 추세, 팝업 운영 기간 정보를 분석해 다음 달 예상 매출(원화 정수)과 운영 전략을 반드시 아래 JSON 형식으로만 응답하세요.
                개별 팝업스토어가 다음 달 중 종료되거나 이미 종료 예정이라면, 실제 영업 가능 일수를 반영해 현실적으로 예측하세요.
                마크다운 코드블록(```)이나 부가 설명 문장은 절대 포함하지 마세요.
                {
                  "expectedNextMonthSales": 0,
                  "conservativeSales": 0,
                  "optimisticSales": 0,
                  "trendSummary": "전월 대비 당월 추세 및 다음 달 전망 한 줄 요약 (한국어)",
                  "actionItems": [
                    "실행 가능한 매출 증대 전략 1 (한국어)",
                    "실행 가능한 매출 증대 전략 2 (한국어)"
                  ]
                }
                """;

            // 4. 유저 프롬프트: DB/Java에서 산출한 실제 수치를 LLM에 주입한다.
            String userPrompt = String.format("""
                - 분석 대상: %s
                - 운영 기간 정보: %s
                - 예측 대상 월: %s
                - 저번 달 확정 매출: %d원 (주문 %d건)
                - 이번 달 현재까지 확정 매출: %d원 (주문 %d건)
                - 이번 달 말 예상 총 매출(일평균 환산): %d원
                - 전월 대비 이번 달 예상 증감률: %.1f%%
                
                위 수치를 근거로 %s의 기본 예상 매출(expectedNextMonthSales), 보수적 예상 매출(conservativeSales), 낙관적 예상 매출(optimisticSales)을 원 단위로 산출하고 분석 요약과 액션 아이템 2~3개를 작성해 주세요.
                """,
                    scopeTitle, operationPeriodNote, nextMonthLabel,
                    lastMonthSales, lastMonthOrders,
                    currentMonthSales, currentMonthOrders,
                    estimatedCurrentSales, growthRatePercent,
                    nextMonthLabel
            );

            // 5. OpenAI 호환 Chat Completions 요청 바디 구성
            //    - temperature: 0.3 -> 수치가 매번 튀지 않도록 일관성을 높임
            //    - response_format: json_object -> Llama가 순수 JSON만 출력하도록 강제
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

            // 6. Groq API POST 호출 및 응답 본문 수신
            String rawResponse = restClient.post()
                    .uri(apiUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            // 7. OpenAI 호환 응답 구조(choices[0].message.content)에서 실제 JSON 문자열을 꺼내 파싱
            JsonNode root = objectMapper.readTree(rawResponse);
            String content = root.path("choices").get(0).path("message").path("content").asString();
            JsonNode forecastJson = objectMapper.readTree(content);

            // LLM이 특정 필드를 누락하더라도 기본 계산값으로 안전하게 채워 넣는다.
            long expected = forecastJson.path("expectedNextMonthSales").asLong(estimatedCurrentSales);
            long conservative = forecastJson.path("conservativeSales").asLong(Math.round(expected * 0.88));
            long optimistic = forecastJson.path("optimisticSales").asLong(Math.round(expected * 1.12));
            String summary = forecastJson.path("trendSummary").asString("전월 및 당월 판매 추세를 반영한 다음 달 예상 매출입니다.");

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
            // 8. Groq 서버 장애, 호출 한도 초과(429), 파싱 실패 시 폴백 결과 반환
            log.error("Groq LLM 호출 실패 - 폴백 결과 반환: {}", e.getMessage());
            return buildFallbackResult(lastMonthSales, estimatedCurrentSales, growthRatePercent);
        }
    }

    /**
     * [안전장치] 외부 LLM 장애 시 Java 통계 추세만으로 예상 매출을 계산해 주는 폴백 메서드
     * - 증감률을 -20% ~ +30% 범위로 보정(Clamp)하고 절반(0.5)만 반영해 비현실적인 폭등/폭락 예측을 방지한다.
     */
    private LlmForecastResult buildFallbackResult(long lastMonthSales, long estimatedCurrentSales, double growthRatePercent) {
        long base = estimatedCurrentSales > 0 ? estimatedCurrentSales : lastMonthSales;
        double clampedRate = Math.clamp(growthRatePercent / 100.0, -0.2, 0.3);
        long expected = Math.max(0L, Math.round(base * (1.0 + clampedRate * 0.5)));

        return new LlmForecastResult(
                expected,
                Math.round(expected * 0.85),
                Math.round(expected * 1.15),
                "전월 실적과 당월 일평균 매출 추세를 반영해 산출된 기본 예측치입니다.",
                List.of(
                        "당월 판매 상위 굿즈 중심의 번들 상품 구성 검토",
                        "팝업 종료 임박 시점 한정 수량 프로모션 운영"
                ),
                true // 프론트엔드에서 필요시 '통계 기반 예측' 배지 등을 표시할 수 있도록 플래그 전달
        );
    }
}
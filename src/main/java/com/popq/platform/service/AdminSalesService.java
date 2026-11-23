package com.popq.platform.service;

import com.popq.platform.dto.PopupSalesResponse;
import com.popq.platform.sales.dto.SalesForecastResponse;
import com.popq.platform.dto.SalesSummaryResponse;
import com.popq.platform.entity.PopupStore;
import com.popq.platform.entity.Role;
import com.popq.platform.entity.User;
import com.popq.platform.order.entity.OrderStatus;
import com.popq.platform.order.repository.OrderItemRepository;
import com.popq.platform.repository.PopupStoreRepository;
import com.popq.platform.repository.SalesSummaryProjection;
import com.popq.platform.repository.UserRepository;
import com.popq.platform.sales.service.GroqForecastClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSalesService {

    private final OrderItemRepository orderItemRepository;
    private final PopupStoreRepository popupStoreRepository;
    private final UserRepository userRepository;
    private final GroqForecastClient groqForecastClient;

    // [인메모리 캐시]
    // - 여러 스레드가 동시 접근해도 안전하도록 ConcurrentHashMap 사용
    // - Key 형식: "ALL_2026-11-23" 또는 "POPUP_1_2026-11-23"
    // - 대시보드 새로고침마다 LLM API를 중복 호출해 무료 한도를 소모하고 로딩이 느려지는 것을 방지
    private final Map<String, SalesForecastResponse> forecastCache = new ConcurrentHashMap<>();

    // 플랫폼 전체 누적 확정 매출 조회
    public SalesSummaryResponse getTotalSales(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("PopupStore not found"));

        if (user.getRole() != Role.ADMIN) {
            throw new IllegalArgumentException("전체 매출은 관리자만 조회할 수 있습니다.");
        }

        SalesSummaryProjection result = orderItemRepository.findSalesSummary(OrderStatus.PAID);

        return new SalesSummaryResponse(
                result.getTotalSales(),
                result.getTotalOrderCount(),
                result.getTotalQuantity()
        );
    }

    // 특정 팝업 누적 확정 매출 조회
    public PopupSalesResponse getPopupSales(String username, Long popId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원을 찾을 수 없습니다."));

        PopupStore popupStore = popupStoreRepository.findById(popId)
                .orElseThrow(() -> new IllegalArgumentException("해당 팝업을 찾을 수 없습니다."));

        if (user.getRole() == Role.MANAGER) {
            if (popupStore.getManager() == null || !popupStore.getManager().getId().equals(user.getId())) {
                throw new IllegalArgumentException("담당 팝업의 매출만 조회할 수 있습니다.");
            }
        }

        SalesSummaryProjection result = orderItemRepository.findSalesSummaryByPopup(popId, OrderStatus.PAID);

        return new PopupSalesResponse(
                popupStore.getPopId(),
                popupStore.getTitle(),
                result.getTotalSales(),
                result.getTotalOrderCount(),
                result.getTotalQuantity()
        );
    }

    /**
     * 플랫폼 전체 다음 달 예상 매출 조회 (ADMIN 전용)
     * @param refresh true면 기존 캐시를 무시하고 Groq API를 새로 호출해 갱신
     */
    public SalesForecastResponse forecastTotalSales(String username, boolean refresh) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원을 찾을 수 없습니다."));

        if (user.getRole() != Role.ADMIN) {
            throw new IllegalArgumentException("전체 매출 예측은 관리자만 조회할 수 있습니다.");
        }

        String cacheKey = "ALL_" + LocalDate.now();
        if (!refresh && forecastCache.containsKey(cacheKey)) {
            return forecastCache.get(cacheKey);
        }

        SalesForecastResponse response = buildForecast(
                null,
                "전체 팝업스토어 (플랫폼 통합)",
                "상시 운영 (플랫폼 내 진행 중인 전체 팝업스토어 합산 기준)"
        );
        forecastCache.put(cacheKey, response);
        return response;
    }

    /**
     * 개별 팝업 다음 달 예상 매출 조회 (ADMIN 전체 허용, MANAGER 본인 담당 팝업만 허용)
     */
    public SalesForecastResponse forecastPopupSales(String username, Long popId, boolean refresh) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원을 찾을 수 없습니다."));

        PopupStore popupStore = popupStoreRepository.findById(popId)
                .orElseThrow(() -> new IllegalArgumentException("해당 팝업을 찾을 수 없습니다."));

        if (user.getRole() == Role.MANAGER) {
            if (popupStore.getManager() == null || !popupStore.getManager().getId().equals(user.getId())) {
                throw new IllegalArgumentException("담당 팝업의 매출 예측만 조회할 수 있습니다.");
            }
        }

        String cacheKey = "POPUP_" + popId + "_" + LocalDate.now();
        if (!refresh && forecastCache.containsKey(cacheKey)) {
            return forecastCache.get(cacheKey);
        }

        // 팝업의 실제 시작일~종료일을 프롬프트에 넘겨, 다음 달 도중 종료되는 팝업은 남은 일수를 반영하도록 유도
        String periodNote = String.format(
                "운영 기간: %s ~ %s (현재 상태: %s)",
                popupStore.getStartDate(),
                popupStore.getEndDate(),
                popupStore.getOpStatus()
        );

        SalesForecastResponse response = buildForecast(popId, popupStore.getTitle(), periodNote);
        forecastCache.put(cacheKey, response);
        return response;
    }

    /**
     * 전월·당월 집계 -> 당월 월말 추정치 및 증감률 계산 -> LLM 예측 요청 -> DTO 조립
     */
    private SalesForecastResponse buildForecast(Long popId, String scopeTitle, String operationPeriodNote) {
        LocalDate today = LocalDate.now();
        YearMonth currentYm = YearMonth.from(today);
        YearMonth lastYm = currentYm.minusMonths(1);
        YearMonth nextYm = currentYm.plusMonths(1);

        // 조회 기간 경계 설정
        //    - 저번 달: [저번 달 1일 00:00 ~ 이번 달 1일 00:00)
        //    - 이번 달: [이번 달 1일 00:00 ~ 내일 00:00)
        LocalDateTime lastStart = lastYm.atDay(1).atStartOfDay();
        LocalDateTime currentStart = currentYm.atDay(1).atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();

        // popId 유무에 따라 전체 집계 쿼리 또는 팝업별 집계 쿼리 분기 실행
        SalesSummaryProjection lastSummary = (popId == null)
                ? orderItemRepository.findSalesSummaryBetween(OrderStatus.PAID, lastStart, currentStart)
                : orderItemRepository.findSalesSummaryByPopupBetween(popId, OrderStatus.PAID, lastStart, currentStart);

        SalesSummaryProjection currentSummary = (popId == null)
                ? orderItemRepository.findSalesSummaryBetween(OrderStatus.PAID, currentStart, tomorrowStart)
                : orderItemRepository.findSalesSummaryByPopupBetween(popId, OrderStatus.PAID, currentStart, tomorrowStart);

        long lastSales = lastSummary.getTotalSales();
        long currentSales = currentSummary.getTotalSales();

        // 이번 달 말 예상 총 매출 계산 (일평균 환산법)
        //    - 예: 오늘이 15일이고 이번 달 총 일수가 30일이며 현재 매출이 100만 원이면,
        //          이번 달 말 예상 매출 = (100만 / 15) * 30 = 200만 원
        //    - 월 중순 시점에서 저번 달(30일치)과 공정하게 비교하기 위해 일평균으로 보정
        int elapsedDays = today.getDayOfMonth();
        int totalDaysInMonth = currentYm.lengthOfMonth();
        long estimatedCurrentSales = (elapsedDays > 0)
                ? Math.round(((double) currentSales / elapsedDays) * totalDaysInMonth)
                : currentSales;

        // 전월 대비 이번 달 예상 증감률(%) 계산
        double growthRate = (lastSales > 0)
                ? ((double) (estimatedCurrentSales - lastSales) / lastSales) * 100.0
                : (estimatedCurrentSales > 0 ? 100.0 : 0.0);

        // 계산된 정량 수치를 Groq(Llama 3)에 전달하여 다음 달 예상 매출 시나리오와 전략 획득
        GroqForecastClient.LlmForecastResult llmResult = groqForecastClient.requestNextMonthForecast(
                scopeTitle,
                operationPeriodNote,
                nextYm.toString(),
                lastSales,
                lastSummary.getTotalOrderCount(),
                currentSales,
                currentSummary.getTotalOrderCount(),
                estimatedCurrentSales,
                growthRate
        );

        // 최종 응답 DTO 조립
        return SalesForecastResponse.builder()
                .popId(popId)
                .popTitle(scopeTitle)
                .lastMonthSales(lastSales)
                .currentMonthSales(currentSales)
                .estimatedCurrentSales(estimatedCurrentSales)
                .growthRatePercent(Math.round(growthRate * 10.0) / 10.0) // 소수점 첫째 자리까지 반올림
                .targetNextMonth(nextYm.toString())
                .expectedNextMonthSales(llmResult.expectedNextMonthSales())
                .conservativeSales(llmResult.conservativeSales())
                .optimisticSales(llmResult.optimisticSales())
                .trendSummary(llmResult.trendSummary())
                .actionItems(llmResult.actionItems())
                .fallback(llmResult.fallback())
                .generatedAt(LocalDateTime.now())
                .build();
    }
}
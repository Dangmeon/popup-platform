package com.popq.platform.sales.service;

import com.popq.platform.sales.dto.PopupSalesResponse;
import com.popq.platform.sales.dto.SalesForecastResponse;
import com.popq.platform.sales.dto.SalesSummaryResponse;
import com.popq.platform.popup.entity.PopupStore;
import com.popq.platform.auth.entity.Role;
import com.popq.platform.auth.entity.User;
import com.popq.platform.order.entity.OrderStatus;
import com.popq.platform.order.repository.OrderItemRepository;
import com.popq.platform.popup.repository.PopupStoreRepository;
import com.popq.platform.sales.repository.SalesSummaryProjection;
import com.popq.platform.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
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
     * 전체 팝업 매출 조회 이건 월단위로 계산
     */
    public SalesForecastResponse forecastTotalSales(String username, boolean refresh) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getRole() != Role.ADMIN) {
            throw new IllegalArgumentException("전체 매출 예측은 관리자만 조회할 수 있습니다.");
        }

        String cacheKey = "ALL_MONTH_" + LocalDate.now();
        if (!refresh && forecastCache.containsKey(cacheKey)) {
            return forecastCache.get(cacheKey);
        }

        SalesForecastResponse response = buildMonthlyForecast(
                "전체 팝업스토어 (플랫폼 통합)",
                "상시 운영 (플랫폼 내 진행 중인 전체 팝업스토어 합산 기준)"
        );
        forecastCache.put(cacheKey, response);
        return response;
    }

    /**
     * 개별 팝업 다음 주 매출 조회 이건 주단위로 계산
     */
    public SalesForecastResponse forecastPopupSales(String username, Long popId, boolean refresh) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        PopupStore popupStore = popupStoreRepository.findById(popId)
                .orElseThrow(() -> new IllegalArgumentException("PopupStore not found"));

        if (user.getRole() == Role.MANAGER) {
            if (popupStore.getManager() == null || !popupStore.getManager().getId().equals(user.getId())) {
                throw new IllegalArgumentException("담당 팝업의 매출 예측만 조회할 수 있습니다.");
            }
        }

        String cacheKey = "POPUP_WEEK_" + popId + "_" + LocalDate.now();
        if (!refresh && forecastCache.containsKey(cacheKey)) {
            return forecastCache.get(cacheKey);
        }

        String periodNote = String.format(
                "운영 기간: %s ~ %s (현재 상태: %s)",
                popupStore.getStartDate(),
                popupStore.getEndDate(),
                popupStore.getOpStatus()
        );

        SalesForecastResponse response = buildWeeklyPopupForecast(popId, popupStore.getTitle(), periodNote);
        forecastCache.put(cacheKey, response);
        return response;
    }

    /**
     * 월 단위 기준 - 플랫폼 전체 매출 예측 (전월 - 당월 -> 익월)
     */
    private SalesForecastResponse buildMonthlyForecast(String scopeTitle, String operationPeriodNote) {
        LocalDate today = LocalDate.now();
        YearMonth currentYm = YearMonth.from(today);
        YearMonth lastYm = currentYm.minusMonths(1);
        YearMonth nextYm = currentYm.plusMonths(1);

        LocalDateTime lastStart = lastYm.atDay(1).atStartOfDay();
        LocalDateTime currentStart = currentYm.atDay(1).atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();

        SalesSummaryProjection lastSummary =
                orderItemRepository.findSalesSummaryBetween(OrderStatus.PAID, lastStart, currentStart);
        SalesSummaryProjection currentSummary =
                orderItemRepository.findSalesSummaryBetween(OrderStatus.PAID, currentStart, tomorrowStart);

        long lastSales = lastSummary.getTotalSales();
        long currentSales = currentSummary.getTotalSales();

        int elapsedDays = today.getDayOfMonth();
        int totalDaysInMonth = currentYm.lengthOfMonth();
        long estimatedCurrentSales = (elapsedDays > 0)
                ? Math.round(((double) currentSales / elapsedDays) * totalDaysInMonth)
                : currentSales;

        double growthRate = (lastSales > 0)
                ? ((double) (estimatedCurrentSales - lastSales) / lastSales) * 100.0
                : (estimatedCurrentSales > 0 ? 100.0 : 0.0);

        GroqForecastClient.LlmForecastResult llmResult = groqForecastClient.requestSalesForecast(
                "MONTH",
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

        return SalesForecastResponse.builder()
                .popId(null)
                .popTitle(scopeTitle)
                .periodUnit("MONTH")
                .lastPeriodSales(lastSales)
                .currentPeriodSales(currentSales)
                .estimatedCurrentSales(estimatedCurrentSales)
                .growthRatePercent(Math.round(growthRate * 10.0) / 10.0)
                .targetNextPeriod(nextYm.toString())
                .expectedNextSales(llmResult.expectedNextSales())
                .conservativeSales(llmResult.conservativeSales())
                .optimisticSales(llmResult.optimisticSales())
                .trendSummary(llmResult.trendSummary())
                .actionItems(llmResult.actionItems())
                .fallback(llmResult.fallback())
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     *  주 단위 기준 - 개별 팝업스토어 매출 예측 (저번 주 - 이번 주 -> 다음 주)
     * - 기준: 월요일 00:00:00 ~ 다음 주 월요일 00:00:00 미만 (7일 단위)
     */
    private SalesForecastResponse buildWeeklyPopupForecast(Long popId, String scopeTitle, String operationPeriodNote) {
        LocalDate today = LocalDate.now();

        // 주간 경계 계산 (월요일 시작 기준)
        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lastMonday = thisMonday.minusWeeks(1);
        LocalDate nextMonday = thisMonday.plusWeeks(1);
        LocalDate nextSunday = nextMonday.plusDays(6);

        LocalDateTime lastWeekStart = lastMonday.atStartOfDay();
        LocalDateTime thisWeekStart = thisMonday.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();

        // 저번 주(월~일 7일) 및 이번 주(월~오늘) 결제 확정된 매출 조회
        SalesSummaryProjection lastSummary =
                orderItemRepository.findSalesSummaryByPopupBetween(popId, OrderStatus.PAID, lastWeekStart, thisWeekStart);
        SalesSummaryProjection currentSummary =
                orderItemRepository.findSalesSummaryByPopupBetween(popId, OrderStatus.PAID, thisWeekStart, tomorrowStart);

        long lastSales = lastSummary.getTotalSales();
        long currentSales = currentSummary.getTotalSales();

        // 이번 주 말 예상 총 매출 계산 (월요일~오늘 경과 일수 기준 7일 환산)
        // 예) 오늘이 화요일(2일 경과)이고 이번 주 매출이 10만 원이면 -> (10만 / 2) * 7 = 35만 원
        int elapsedDaysInWeek = (int) ChronoUnit.DAYS.between(thisMonday, today) + 1;
        long estimatedCurrentSales = (elapsedDaysInWeek > 0)
                ? Math.round(((double) currentSales / elapsedDaysInWeek) * 7.0)
                : currentSales;

        // 저번 주 대비 이번 주 예상 증감률(%)
        double growthRate = (lastSales > 0)
                ? ((double) (estimatedCurrentSales - lastSales) / lastSales) * 100.0
                : (estimatedCurrentSales > 0 ? 100.0 : 0.0);

        // 다음 주 기간 라벨 (예: "2026-11-30 ~ 2026-12-06")
        String nextWeekLabel = nextMonday + " ~ " + nextSunday;

        GroqForecastClient.LlmForecastResult llmResult = groqForecastClient.requestSalesForecast(
                "WEEK",
                scopeTitle,
                operationPeriodNote,
                nextWeekLabel,
                lastSales,
                lastSummary.getTotalOrderCount(),
                currentSales,
                currentSummary.getTotalOrderCount(),
                estimatedCurrentSales,
                growthRate
        );

        return SalesForecastResponse.builder()
                .popId(popId)
                .popTitle(scopeTitle)
                .periodUnit("WEEK")
                .lastPeriodSales(lastSales)
                .currentPeriodSales(currentSales)
                .estimatedCurrentSales(estimatedCurrentSales)
                .growthRatePercent(Math.round(growthRate * 10.0) / 10.0)
                .targetNextPeriod(nextWeekLabel)
                .expectedNextSales(llmResult.expectedNextSales())
                .conservativeSales(llmResult.conservativeSales())
                .optimisticSales(llmResult.optimisticSales())
                .trendSummary(llmResult.trendSummary())
                .actionItems(llmResult.actionItems())
                .fallback(llmResult.fallback())
                .generatedAt(LocalDateTime.now())
                .build();
    }
}
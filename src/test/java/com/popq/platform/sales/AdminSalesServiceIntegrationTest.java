package com.popq.platform.sales;

import com.popq.platform.auth.entity.Role;
import com.popq.platform.auth.entity.User;
import com.popq.platform.auth.repository.UserRepository;
import com.popq.platform.order.entity.Order;
import com.popq.platform.order.entity.OrderItem;
import com.popq.platform.order.repository.OrderItemRepository;
import com.popq.platform.order.repository.OrderRepository;
import com.popq.platform.popup.entity.PopupStore;
import com.popq.platform.popup.repository.PopupStoreRepository;
import com.popq.platform.product.entity.Product;
import com.popq.platform.product.repository.ProductRepository;
import com.popq.platform.sales.dto.MonthlySalesResponse;
import com.popq.platform.sales.dto.PopupSalesResponse;
import com.popq.platform.sales.dto.SalesForecastResponse;
import com.popq.platform.sales.dto.SalesSummaryResponse;
import com.popq.platform.sales.dto.WeekSalesResponse;
import com.popq.platform.sales.exception.SalesErrorCode;
import com.popq.platform.sales.exception.SalesException;
import com.popq.platform.sales.service.AdminSalesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AdminSalesServiceIntegrationTest {

    @Autowired
    private AdminSalesService adminSalesService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PopupStoreRepository popupStoreRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    private User adminUser;
    private User managerA;
    private User managerB;
    private User buyerUser;

    private PopupStore popupA;
    private PopupStore popupB;

    private Product productA;
    private Product productB;

    private final List<Long> createdOrderIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        adminUser = userRepository.save(User.builder()
                .username("junit_sales_admin@test.com")
                .password("encodedPw")
                .name("매출총괄관리자")
                .phone("010-9898-0001")
                .role(Role.ADMIN)
                .build());

        managerA = userRepository.save(User.builder()
                .username("junit_sales_managera@test.com")
                .password("encodedPw")
                .name("팝업A매니저")
                .phone("010-9898-0002")
                .role(Role.MANAGER)
                .build());

        managerB = userRepository.save(User.builder()
                .username("junit_sales_managerb@test.com")
                .password("encodedPw")
                .name("팝업B매니저")
                .phone("010-9898-0003")
                .role(Role.MANAGER)
                .build());

        buyerUser = userRepository.save(User.builder()
                .username("junit_sales_buyer@test.com")
                .password("encodedPw")
                .name("테스트구매자")
                .phone("010-9898-0004")
                .role(Role.USER)
                .build());

        LocalDate today = LocalDate.now();
        popupA = popupStoreRepository.save(PopupStore.builder()
                .title("[JUnit] 매출테스트 팝업 A")
                .location("서울 성수동")
                .startDate(today.minusMonths(2))
                .endDate(today.plusMonths(1))
                .category("FASHION")
                .opStatus("OPEN")
                .manager(managerA)
                .build());

        popupB = popupStoreRepository.save(PopupStore.builder()
                .title("[JUnit] 매출테스트 팝업 B")
                .location("서울 홍대")
                .startDate(today.minusMonths(2))
                .endDate(today.plusMonths(1))
                .category("CHARACTER")
                .opStatus("OPEN")
                .manager(managerB)
                .build());

        productA = productRepository.save(Product.builder()
                .popupStore(popupA)
                .productName("[JUnit] 팝업A 한정 굿즈")
                .price(10000)
                .stock(1000)
                .category("GOODS")
                .maxQty(10)
                .build());

        productB = productRepository.save(Product.builder()
                .popupStore(popupB)
                .productName("[JUnit] 팝업B 한정 굿즈")
                .price(20000)
                .stock(1000)
                .category("GOODS")
                .maxQty(10)
                .build());
    }

    @AfterEach
    void tearDown() {
        if (!createdOrderIds.isEmpty()) {
            List<Order> orders = orderRepository.findAllById(createdOrderIds);
            orderRepository.deleteAll(orders);
            createdOrderIds.clear();
        }
        productRepository.deleteAllInBatch(List.of(productA, productB));
        popupStoreRepository.deleteAllInBatch(List.of(popupA, popupB));
        userRepository.deleteAllInBatch(List.of(adminUser, managerA, managerB, buyerUser));
    }

    @Test
    @DisplayName("집계 1: 전체 및 팝업별 확정 매출 조회 시 PAID 상태만 합산되고 PENDING/CANCELLED/EXPIRED는 제외된다")
    void totalAndPopupSales_OnlyPaidIncluded() {
        // 기존 DB 데이터 대비 증가분 측정을 위한 기준선 조회
        SalesSummaryResponse beforeTotal = adminSalesService.getTotalSales(adminUser.getUsername());

        // 팝업 A: PAID 2건 (10,000 + 30,000 = 40,000원), PENDING 1건(50,000원), CANCELLED 1건(70,000원), EXPIRED 1건(90,000원)
        savePaidOrder(productA, 10000, LocalDateTime.now());
        savePaidOrder(productA, 30000, LocalDateTime.now());
        saveUnpaidOrder(productA, 50000, "PENDING");
        saveUnpaidOrder(productA, 70000, "CANCELLED");
        saveUnpaidOrder(productA, 90000, "EXPIRED");

        // 팝업 B: PAID 1건 (20,000원)
        savePaidOrder(productB, 20000, LocalDateTime.now());

        // 1. 개별 팝업 A 확정 매출 검증 (담당 매니저 A 조회)
        PopupSalesResponse popupASales = adminSalesService.getPopupSales(managerA.getUsername(), popupA.getPopId());
        assertThat(popupASales.getTotalSales()).isEqualTo(40000L);
        assertThat(popupASales.getTotalOrderCount()).isEqualTo(2L);
        assertThat(popupASales.getTotalQuantity()).isEqualTo(2L);

        // 2. 개별 팝업 B 확정 매출 검증 (ADMIN 조회)
        PopupSalesResponse popupBSales = adminSalesService.getPopupSales(adminUser.getUsername(), popupB.getPopId());
        assertThat(popupBSales.getTotalSales()).isEqualTo(20000L);
        assertThat(popupBSales.getTotalOrderCount()).isEqualTo(1L);

        // 3. 플랫폼 전체 확정 매출 증가분 검증 (40,000 + 20,000 = +60,000원, 주문 +3건)
        SalesSummaryResponse afterTotal = adminSalesService.getTotalSales(adminUser.getUsername());
        assertThat(afterTotal.getTotalSales() - beforeTotal.getTotalSales()).isEqualTo(60000L);
        assertThat(afterTotal.getTotalOrderCount() - beforeTotal.getTotalOrderCount()).isEqualTo(3L);
        assertThat(afterTotal.getTotalQuantity() - beforeTotal.getTotalQuantity()).isEqualTo(3L);
    }

    @Test
    @DisplayName("추이 2: 최근 4개월 월별 매출 조회 시 4개 요소가 반환되며 매출이 없는 달은 0L로 채워진다")
    void monthlySales_ReturnsFourMonthsWithZeroFill() {
        List<MonthlySalesResponse> beforeList = adminSalesService.getMonthlySales(adminUser.getUsername());
        assertThat(beforeList).hasSize(4);

        YearMonth currentYm = YearMonth.now();
        YearMonth lastYm = currentYm.minusMonths(1);

        long beforeLastMonth = findMonthSales(beforeList, lastYm.toString());
        long beforeCurrentMonth = findMonthSales(beforeList, currentYm.toString());

        // 저번 달 15일 결제 25,000원, 이번 달 1일 00:05 결제 45,000원 생성
        savePaidOrder(productA, 25000, lastYm.atDay(15).atTime(14, 0));
        savePaidOrder(productB, 45000, currentYm.atDay(1).atTime(0, 5));

        List<MonthlySalesResponse> afterList = adminSalesService.getMonthlySales(adminUser.getUsername());
        assertThat(afterList).hasSize(4);
        assertThat(findMonthSales(afterList, lastYm.toString()) - beforeLastMonth).isEqualTo(25000L);
        assertThat(findMonthSales(afterList, currentYm.toString()) - beforeCurrentMonth).isEqualTo(45000L);
    }

    @Test
    @DisplayName("추이 3: 특정 팝업 최근 4주 주별 매출 조회 시 월~일 주간 경계별로 정확히 합산된다")
    void weeklyPopupSales_ReturnsFourWeeksGroupedByMonday() {
        LocalDate baseDate = LocalDate.now();
        LocalDate thisMonday = baseDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lastMonday = thisMonday.minusWeeks(1);

        // 저번 주 월요일(10,000원) + 저번 주 일요일(15,000원) -> 저번 주 합계 25,000원
        savePaidOrder(productA, 10000, lastMonday.atTime(10, 0));
        savePaidOrder(productA, 15000, lastMonday.plusDays(6).atTime(23, 30));

        // 이번 주 월요일(30,000원) -> 이번 주 합계 30,000원
        savePaidOrder(productA, 30000, thisMonday.atTime(9, 0));

        List<WeekSalesResponse> weekly = adminSalesService.getWeeklySales(
                managerA.getUsername(), popupA.getPopId(), baseDate
        );

        assertThat(weekly).hasSize(4);
        // index 0: 3주 전, index 1: 2주 전, index 2: 저번 주, index 3: 이번 주
        assertThat(weekly.get(0).totalSales()).isZero();
        assertThat(weekly.get(1).totalSales()).isZero();
        assertThat(weekly.get(2).weekStart()).isEqualTo(lastMonday);
        assertThat(weekly.get(2).totalSales()).isEqualTo(25000L);
        assertThat(weekly.get(3).weekStart()).isEqualTo(thisMonday);
        assertThat(weekly.get(3).totalSales()).isEqualTo(30000L);
    }

    @Test
    @DisplayName("예측 4: AI 매출 예측 시 폴백 통계 계산, 인메모리 캐시 히트 및 refresh=true 갱신이 정상 동작한다")
    void salesForecast_FallbackAndCacheBehavior() {
        LocalDate today = LocalDate.now();
        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lastMonday = thisMonday.minusWeeks(1);

        savePaidOrder(productA, 100000, lastMonday.plusDays(2).atTime(12, 0));
        savePaidOrder(productA, 50000, thisMonday.atTime(12, 0));

        // 1. 최초 호출 (refresh=false) -> 폴백 또는 정상 예측 결과 반환
        SalesForecastResponse firstCall = adminSalesService.forecastPopupSales(
                managerA.getUsername(), popupA.getPopId(), false
        );
        assertThat(firstCall.getPopId()).isEqualTo(popupA.getPopId());
        assertThat(firstCall.getPeriodUnit()).isEqualTo("WEEK");
        assertThat(firstCall.getLastPeriodSales()).isEqualTo(100000L);
        assertThat(firstCall.getCurrentPeriodSales()).isEqualTo(50000L);
        assertThat(firstCall.getExpectedNextSales()).isPositive();
        assertThat(firstCall.getActionItems()).isNotEmpty();

        // 2. 캐시 히트 검증 (refresh=false 재호출 시 generatedAt 동일)
        SalesForecastResponse cachedCall = adminSalesService.forecastPopupSales(
                managerA.getUsername(), popupA.getPopId(), false
        );
        assertThat(cachedCall.getGeneratedAt()).isEqualTo(firstCall.getGeneratedAt());

        // 3. 강제 갱신 검증 (refresh=true 호출 시 새로 생성)
        SalesForecastResponse refreshedCall = adminSalesService.forecastPopupSales(
                managerA.getUsername(), popupA.getPopId(), true
        );
        assertThat(refreshedCall.getGeneratedAt()).isAfterOrEqualTo(firstCall.getGeneratedAt());
    }

    @Test
    @DisplayName("권한 5: 플랫폼 전체 매출·월별 추이·전체 예측은 ADMIN 전용이며 MANAGER와 USER는 차단된다")
    void adminOnlyApis_Fail_WhenManagerOrUserAccess() {
        // MANAGER 차단 검증
        assertThatThrownBy(() -> adminSalesService.getTotalSales(managerA.getUsername()))
                .isInstanceOf(SalesException.class)
                .extracting("errorCode")
                .isEqualTo(SalesErrorCode.FORBIDDEN_ADMIN_ONLY);

        assertThatThrownBy(() -> adminSalesService.getMonthlySales(managerA.getUsername()))
                .isInstanceOf(SalesException.class)
                .extracting("errorCode")
                .isEqualTo(SalesErrorCode.FORBIDDEN_ADMIN_ONLY);

        assertThatThrownBy(() -> adminSalesService.forecastTotalSales(managerA.getUsername(), false))
                .isInstanceOf(SalesException.class)
                .extracting("errorCode")
                .isEqualTo(SalesErrorCode.FORBIDDEN_ADMIN_ONLY);

        // 일반 USER 차단 검증
        assertThatThrownBy(() -> adminSalesService.getTotalSales(buyerUser.getUsername()))
                .isInstanceOf(SalesException.class)
                .extracting("errorCode")
                .isEqualTo(SalesErrorCode.FORBIDDEN_ADMIN_ONLY);
    }

    @Test
    @DisplayName("권한 6: 타 매니저 담당 팝업의 매출·주별 추이·예측 조회 요청 시 POPUP_ACCESS_DENIED로 차단된다")
    void popupSalesApis_Fail_WhenOtherManagerAccess() {
        // managerA가 managerB 소유의 popupB 매출에 접근 시도
        assertThatThrownBy(() -> adminSalesService.getPopupSales(managerA.getUsername(), popupB.getPopId()))
                .isInstanceOf(SalesException.class)
                .extracting("errorCode")
                .isEqualTo(SalesErrorCode.POPUP_ACCESS_DENIED);

        assertThatThrownBy(() -> adminSalesService.getWeeklySales(managerA.getUsername(), popupB.getPopId(), LocalDate.now()))
                .isInstanceOf(SalesException.class)
                .extracting("errorCode")
                .isEqualTo(SalesErrorCode.POPUP_ACCESS_DENIED);

        assertThatThrownBy(() -> adminSalesService.forecastPopupSales(managerA.getUsername(), popupB.getPopId(), false))
                .isInstanceOf(SalesException.class)
                .extracting("errorCode")
                .isEqualTo(SalesErrorCode.POPUP_ACCESS_DENIED);
    }

    // --- 헬퍼 메서드 ---

    private void savePaidOrder(Product product, int amount, LocalDateTime paidAt) {
        Order order = Order.createOrder(
                buyerUser, amount, paidAt.plusMinutes(30),
                "수령인", "010-9898-9999", "서울시", "101호", "문앞", null
        );
        order.completePayment();
        ReflectionTestUtils.setField(order, "orderedAt", paidAt);
        ReflectionTestUtils.setField(order, "paymentAt", paidAt);

        OrderItem item = OrderItem.createOrderItem(product, 1, amount, amount);
        order.addOrderItem(item);

        Order saved = orderRepository.save(order);
        createdOrderIds.add(saved.getOrderId());
    }

    private void saveUnpaidOrder(Product product, int amount, String statusName) {
        LocalDateTime now = LocalDateTime.now();
        Order order = Order.createOrder(
                buyerUser, amount, now.plusMinutes(30),
                "수령인", "010-9898-9999", "서울시", "101호", "문앞", null
        );
        if ("CANCELLED".equals(statusName)) {
            order.cancel();
        } else if ("EXPIRED".equals(statusName)) {
            order.expire();
        }
        OrderItem item = OrderItem.createOrderItem(product, 1, amount, amount);
        order.addOrderItem(item);

        Order saved = orderRepository.save(order);
        createdOrderIds.add(saved.getOrderId());
    }

    private long findMonthSales(List<MonthlySalesResponse> list, String yearMonth) {
        return list.stream()
                .filter(m -> m.month().equals(yearMonth))
                .findFirst()
                .map(MonthlySalesResponse::totalSales)
                .orElse(0L);
    }
}
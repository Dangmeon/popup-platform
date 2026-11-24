package com.popq.platform.dashboard.repository;

import com.popq.platform.popup.entity.PopupStore;
import com.popq.platform.product.entity.Product;
import com.popq.platform.order.entity.Order;
import com.popq.platform.order.entity.OrderStatus;
import com.popq.platform.reservation.entity.ReservationStatus;
import com.popq.platform.waiting.entity.WaitingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// 관리자 대시보드 · 예약 많은 순 팝업 목록 집계 (조회만 함)
// managerId : null이면 전체 팝업(ADMIN), 값이 있으면 그 매니저의 담당 팝업만(MANAGER)
// 집계 결과는 XxxProjection 인터페이스로 받음 (AS 뒤의 이름 = get 메서드 이름)
public interface AdminDashboardRepository extends JpaRepository<PopupStore, Long> {

    // 예약 많은 순 팝업 목록 : 예약이 없는 팝업도 0으로 나오도록 LEFT JOIN, 동점이면 시작일 최신순
    @Query("SELECT p.popId AS popId, p.title AS title, p.location AS location, "
            + "p.startDate AS startDate, p.endDate AS endDate, p.opStatus AS opStatus, "
            + "COALESCE(SUM(r.peopleCount), 0) AS reservedPeople, COUNT(r) AS reservationCount "
            + "FROM PopupStore p LEFT JOIN p.manager m "
            + "LEFT JOIN TimeSlot t ON t.popupStore = p "
            + "LEFT JOIN Reservation r ON r.timeSlot = t AND r.reservationStatus IN :statuses AND r.reservedAt >= :fromTime "
            + "WHERE (:managerId IS NULL OR m.id = :managerId) "
            + "GROUP BY p.popId, p.title, p.location, p.startDate, p.endDate, p.opStatus "
            + "ORDER BY COALESCE(SUM(r.peopleCount), 0) DESC, p.startDate DESC")
    List<PopupRankingProjection> findReservationRanking(@Param("managerId") Long managerId,
                                                        @Param("statuses") List<ReservationStatus> statuses,
                                                        @Param("fromTime") LocalDateTime fromTime);

    // 오늘 회차의 예약 건수
    @Query("SELECT COUNT(r) FROM Reservation r JOIN r.timeSlot t JOIN t.popupStore p LEFT JOIN p.manager m "
            + "WHERE t.slotDate = :today AND r.reservationStatus IN :statuses "
            + "AND (:managerId IS NULL OR m.id = :managerId)")
    long countTodayReservations(@Param("managerId") Long managerId,
                                @Param("today") LocalDate today,
                                @Param("statuses") List<ReservationStatus> statuses);

    // 날짜별 예약 수 · 입장(USED) 수 (최근 7일 추이)
    @Query("SELECT t.slotDate AS slotDate, COUNT(r) AS reserved, "
            + "SUM(CASE WHEN r.reservationStatus = :used THEN 1 ELSE 0 END) AS entered "
            + "FROM Reservation r JOIN r.timeSlot t JOIN t.popupStore p LEFT JOIN p.manager m "
            + "WHERE t.slotDate BETWEEN :fromDate AND :toDate AND r.reservationStatus IN :statuses "
            + "AND (:managerId IS NULL OR m.id = :managerId) "
            + "GROUP BY t.slotDate")
    List<DailyReservationProjection> countDailyReservations(@Param("managerId") Long managerId,
                                                            @Param("fromDate") LocalDate fromDate,
                                                            @Param("toDate") LocalDate toDate,
                                                            @Param("statuses") List<ReservationStatus> statuses,
                                                            @Param("used") ReservationStatus used);

    // 팝업별 오늘 회차 정원 · 예약 인원 (혼잡도) : 취소된 회차, 종료(CANCELLED)된 팝업 제외
    @Query("SELECT p.popId AS popId, p.title AS title, SUM(t.capacity) AS capacity, SUM(t.reservedCount) AS reserved "
            + "FROM TimeSlot t JOIN t.popupStore p LEFT JOIN p.manager m "
            + "WHERE t.slotDate = :today AND t.slotStatus = 'OPEN' AND p.opStatus <> 'CANCELLED' "
            + "AND (:managerId IS NULL OR m.id = :managerId) "
            + "GROUP BY p.popId, p.title "
            + "ORDER BY p.popId")
    List<PopupCrowdingProjection> findTodayCrowding(@Param("managerId") Long managerId,
                                                    @Param("today") LocalDate today);

    // 팝업별 현재 웨이팅 수 (오늘 접수, 대기 · 호출 상태)
    @Query("SELECT p.popId AS popId, COUNT(w) AS waitingCount "
            + "FROM WaitingQueue w JOIN w.popupStore p LEFT JOIN p.manager m "
            + "WHERE w.waitingStatus IN :statuses AND w.joinedAt >= :todayStart "
            + "AND (:managerId IS NULL OR m.id = :managerId) "
            + "GROUP BY p.popId")
    List<WaitingCountProjection> countWaitingsByPopup(@Param("managerId") Long managerId,
                                                      @Param("todayStart") LocalDateTime todayStart,
                                                      @Param("statuses") List<WaitingStatus> statuses);

    // 오늘 결제 완료 주문 수 (MANAGER는 담당 팝업 상품이 들어간 주문만)
    @Query("SELECT COUNT(DISTINCT o.orderId) FROM OrderItem oi JOIN oi.order o JOIN oi.product pr "
            + "JOIN pr.popupStore p LEFT JOIN p.manager m "
            + "WHERE o.orderStatus = :paid AND o.paymentAt >= :todayStart AND o.paymentAt < :tomorrowStart "
            + "AND (:managerId IS NULL OR m.id = :managerId)")
    long countTodayPaidOrders(@Param("managerId") Long managerId,
                              @Param("todayStart") LocalDateTime todayStart,
                              @Param("tomorrowStart") LocalDateTime tomorrowStart,
                              @Param("paid") OrderStatus paid);

    // 최근 주문 (결제 대기 제외, 최신순) : 몇 개만 쓸지는 서비스에서 자름
    @Query("SELECT DISTINCT o FROM OrderItem oi JOIN oi.order o JOIN oi.product pr "
            + "JOIN pr.popupStore p LEFT JOIN p.manager m "
            + "WHERE o.orderStatus <> :pending "
            + "AND (:managerId IS NULL OR m.id = :managerId) "
            + "ORDER BY o.orderedAt DESC")
    List<Order> findRecentOrders(@Param("managerId") Long managerId,
                                 @Param("pending") OrderStatus pending);

    // 재고 부족 상품 (판매 종료된 상품 제외, 재고 적은 순)
    @Query("SELECT pr FROM Product pr JOIN pr.popupStore p LEFT JOIN p.manager m "
            + "WHERE pr.stock <= :threshold AND (pr.saleEnd IS NULL OR pr.saleEnd > :now) "
            + "AND (:managerId IS NULL OR m.id = :managerId) "
            + "ORDER BY pr.stock ASC, pr.prdId ASC")
    List<Product> findLowStockProducts(@Param("managerId") Long managerId,
                                       @Param("threshold") int threshold,
                                       @Param("now") LocalDateTime now);
}
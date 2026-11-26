package com.popq.platform.popup;

import com.popq.platform.auth.entity.Role;
import com.popq.platform.auth.entity.User;
import com.popq.platform.auth.repository.UserRepository;
import com.popq.platform.favorite.repository.FavoriteRepository;
import com.popq.platform.popup.dto.PopupStoreRequest;
import com.popq.platform.popup.dto.PopupStoreResponse;
import com.popq.platform.popup.entity.PopupStore;
import com.popq.platform.popup.exception.PopupErrorCode;
import com.popq.platform.popup.exception.PopupException;
import com.popq.platform.popup.repository.PopupStoreRepository;
import com.popq.platform.popup.service.PopupAccessService;
import com.popq.platform.popup.service.PopupStoreService;
import com.popq.platform.product.repository.ProductRepository;
import com.popq.platform.timeslot.repository.TimeSlotRepository;
import com.popq.platform.timeslot.service.TimeSlotService;
import com.popq.platform.waiting.repository.WaitingQueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PopupStoreServiceTest {

    @Mock
    PopupStoreRepository popupRepo;
    @Mock
    TimeSlotRepository timeSlotRepo;
    @Mock
    ProductRepository productRepo;
    @Mock
    PopupAccessService accessService;
    @Mock
    TimeSlotService timeSlotService;
    @Mock
    WaitingQueueRepository waitingQueueRepo;
    @Mock
    FavoriteRepository favoriteRepo;
    @Mock
    UserRepository userRepo;

    PopupStoreService popupService;

    static final String ADMIN = "admin@gmail.com";
    static final String MANAGER = "manager@gmail.com";

    @BeforeEach
    void setUp() {
        popupService = new PopupStoreService(popupRepo, timeSlotRepo, productRepo, accessService,
                timeSlotService, waitingQueueRepo, favoriteRepo, userRepo);
    }

    // 정상 등록 요청 (테스트마다 필요한 값만 바꿔서 사용)
    private PopupStoreRequest createRequest() {
        PopupStoreRequest request = new PopupStoreRequest();
        request.setTitle("테스트 팝업");
        request.setLocation("성수");
        request.setStartDate(LocalDate.of(2026, 12, 1));
        request.setEndDate(LocalDate.of(2026, 12, 10));
        request.setReservStart("10:00");
        request.setReservEnd("20:00");
        request.setReservPeopleCap(10);
        request.setRoundNum(1);
        return request;
    }

    // DB에 저장되어 있는 팝업 (운영 상태만 바꿔서 사용)
    private PopupStore createPopup(String opStatus) {
        return PopupStore.builder()
                .popId(1L)
                .title("테스트 팝업")
                .location("성수")
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 10))
                .category("ETC")
                .reservStart("10:00")
                .reservEnd("20:00")
                .reservPeopleCap(10)
                .opStatus(opStatus)
                .build();
    }

    // 예외가 나는지, 어떤 에러 코드인지 확인
    private void assertErrorCode(PopupErrorCode expected, org.junit.jupiter.api.function.Executable action) {
        PopupException e = assertThrows(PopupException.class, action);
        assertEquals(expected, e.getErrorCode());
    }

    // 팝업 등록

    @Test
    @DisplayName("등록 성공 : ADMIN이 등록하면 담당 매니저 없이 OPEN, 카테고리를 비우면 ETC")
    void insertPopup_admin() {
        User admin = User.builder().username(ADMIN).role(Role.ADMIN).build();
        when(accessService.getLoginUser(ADMIN)).thenReturn(admin);
        when(popupRepo.save(any(PopupStore.class))).thenAnswer(invocation -> invocation.getArgument(0)); // 받은 팝업 그대로 반환

        PopupStoreResponse response = popupService.insertPopup(createRequest(), ADMIN);

        assertEquals("테스트 팝업", response.getTitle());
        assertEquals("OPEN", response.getOpStatus());
        assertEquals("ETC", response.getCategory());
        assertNull(response.getManagerId());
        verify(popupRepo).save(any(PopupStore.class));
    }

    @Test
    @DisplayName("등록 성공 : MANAGER가 등록하면 본인이 담당 매니저로 지정")
    void insertPopup_manager() {
        User manager = User.builder().username(MANAGER).role(Role.MANAGER).build();
        when(accessService.getLoginUser(MANAGER)).thenReturn(manager);
        when(popupRepo.save(any(PopupStore.class))).thenAnswer(invocation -> invocation.getArgument(0));

        popupService.insertPopup(createRequest(), MANAGER);

        verify(popupRepo).save(argThat(popup -> popup.getManager() == manager)); // 저장된 팝업의 매니저가 로그인한 매니저인지
    }

    @Test
    @DisplayName("등록 실패 : 필수값(팝업명)이 없으면 POPUP_REQUIRED_FIELD")
    void insertPopup_requiredField() {
        PopupStoreRequest request = createRequest();
        request.setTitle(" ");

        assertErrorCode(PopupErrorCode.POPUP_REQUIRED_FIELD, () -> popupService.insertPopup(request, ADMIN));
        verify(popupRepo, never()).save(any());
    }

    @Test
    @DisplayName("등록 실패 : 종료일이 시작일보다 빠르면 INVALID_POPUP_PERIOD")
    void insertPopup_invalidPeriod() {
        PopupStoreRequest request = createRequest();
        request.setEndDate(LocalDate.of(2026, 11, 30));

        assertErrorCode(PopupErrorCode.INVALID_POPUP_PERIOD, () -> popupService.insertPopup(request, ADMIN));
    }

    @Test
    @DisplayName("등록 실패 : 예약 시간이 HH:mm 형식이 아니면 INVALID_RESERV_TIME_FORMAT")
    void insertPopup_invalidReservTimeFormat() {
        PopupStoreRequest request = createRequest();
        request.setReservStart("9:00");

        assertErrorCode(PopupErrorCode.INVALID_RESERV_TIME_FORMAT, () -> popupService.insertPopup(request, ADMIN));
    }

    @Test
    @DisplayName("등록 실패 : 예약 종료 시간이 시작 시간보다 빠르거나 같으면 INVALID_RESERV_TIME_RANGE")
    void insertPopup_invalidReservTimeRange() {
        PopupStoreRequest request = createRequest();
        request.setReservEnd("10:00");

        assertErrorCode(PopupErrorCode.INVALID_RESERV_TIME_RANGE, () -> popupService.insertPopup(request, ADMIN));
    }

    @Test
    @DisplayName("등록 실패 : 예약 정원이 1 미만이면 INVALID_RESERV_PEOPLE_CAP")
    void insertPopup_invalidPeopleCap() {
        PopupStoreRequest request = createRequest();
        request.setReservPeopleCap(0);

        assertErrorCode(PopupErrorCode.INVALID_RESERV_PEOPLE_CAP, () -> popupService.insertPopup(request, ADMIN));
    }

    @Test
    @DisplayName("등록 실패 : 허용 목록에 없는 카테고리(CHARACTER)면 INVALID_CATEGORY")
    void insertPopup_invalidCategory() {
        PopupStoreRequest request = createRequest();
        request.setCategory("CHARACTER");

        assertErrorCode(PopupErrorCode.INVALID_CATEGORY, () -> popupService.insertPopup(request, ADMIN));
    }

    // 팝업 수정

    @Test
    @DisplayName("수정 성공 : 기간이 그대로면 회차·상품 충돌 확인을 하지 않음")
    void updatePopup_success() {
        PopupStore popup = createPopup("OPEN");
        when(accessService.checkPopupAccess(1L, ADMIN)).thenReturn(popup);
        PopupStoreRequest request = createRequest();
        request.setTitle("바뀐 팝업명");

        PopupStoreResponse response = popupService.updatePopup(1L, request, ADMIN);

        assertEquals("바뀐 팝업명", response.getTitle());
        verify(timeSlotRepo, never()).existsBeforeStartDate(anyLong(), any());
        verify(productRepo, never()).existsSaleStartBefore(anyLong(), any());
    }

    @Test
    @DisplayName("수정 실패 : 운영 취소된 팝업은 POPUP_CANCELLED_MODIFICATION_DENIED")
    void updatePopup_cancelled() {
        when(accessService.checkPopupAccess(1L, ADMIN)).thenReturn(createPopup("CANCELLED"));

        assertErrorCode(PopupErrorCode.POPUP_CANCELLED_MODIFICATION_DENIED,
                () -> popupService.updatePopup(1L, createRequest(), ADMIN));
    }

    @Test
    @DisplayName("수정 실패 : 시작일을 늦춰서 기간 밖에 회차가 남으면 POPUP_TIMESLOT_OUT_OF_PERIOD")
    void updatePopup_timeslotOutOfPeriod() {
        when(accessService.checkPopupAccess(1L, ADMIN)).thenReturn(createPopup("OPEN"));
        PopupStoreRequest request = createRequest();
        request.setStartDate(LocalDate.of(2026, 12, 5));
        when(timeSlotRepo.existsBeforeStartDate(1L, LocalDate.of(2026, 12, 5))).thenReturn(true);

        assertErrorCode(PopupErrorCode.POPUP_TIMESLOT_OUT_OF_PERIOD, () -> popupService.updatePopup(1L, request, ADMIN));
    }

    @Test
    @DisplayName("수정 실패 : 종료일을 늦춰서 상품 판매 시작일과 겹치면 POPUP_PRODUCT_SALE_CONFLICT")
    void updatePopup_productSaleConflict() {
        when(accessService.checkPopupAccess(1L, ADMIN)).thenReturn(createPopup("OPEN"));
        PopupStoreRequest request = createRequest();
        request.setEndDate(LocalDate.of(2026, 12, 20));
        when(productRepo.existsSaleStartBefore(1L, LocalDate.of(2026, 12, 21).atStartOfDay())).thenReturn(true);

        assertErrorCode(PopupErrorCode.POPUP_PRODUCT_SALE_CONFLICT, () -> popupService.updatePopup(1L, request, ADMIN));
    }

    // 팝업 삭제

    @Test
    @DisplayName("삭제 실패 : MANAGER는 삭제할 수 없음 (ADMIN_ONLY)")
    void deletePopup_manager() {
        doThrow(new PopupException(PopupErrorCode.ADMIN_ONLY)).when(accessService).checkAdmin(MANAGER);

        assertErrorCode(PopupErrorCode.ADMIN_ONLY, () -> popupService.deletePopup(1L, MANAGER));
        verify(popupRepo, never()).delete(any());
    }

    @Test
    @DisplayName("삭제 실패 : 회차가 있으면 POPUP_HAS_TIMESLOTS")
    void deletePopup_hasTimeslots() {
        when(accessService.checkPopupAccess(1L, ADMIN)).thenReturn(createPopup("OPEN"));
        when(timeSlotRepo.existsByPopId(1L)).thenReturn(true);

        assertErrorCode(PopupErrorCode.POPUP_HAS_TIMESLOTS, () -> popupService.deletePopup(1L, ADMIN));
        verify(popupRepo, never()).delete(any());
    }

    @Test
    @DisplayName("삭제 실패 : 상품이 있으면 POPUP_HAS_PRODUCTS")
    void deletePopup_hasProducts() {
        when(accessService.checkPopupAccess(1L, ADMIN)).thenReturn(createPopup("OPEN"));
        when(productRepo.existsByPopId(1L)).thenReturn(true);

        assertErrorCode(PopupErrorCode.POPUP_HAS_PRODUCTS, () -> popupService.deletePopup(1L, ADMIN));
        verify(popupRepo, never()).delete(any());
    }

    @Test
    @DisplayName("삭제 실패 : 웨이팅 입장 완료 기록이 있으면 POPUP_HAS_ENTERED_WAITINGS")
    void deletePopup_hasEnteredWaitings() {
        when(accessService.checkPopupAccess(1L, ADMIN)).thenReturn(createPopup("OPEN"));
        when(popupRepo.existsEnteredWaitingByPopId(1L)).thenReturn(true);

        assertErrorCode(PopupErrorCode.POPUP_HAS_ENTERED_WAITINGS, () -> popupService.deletePopup(1L, ADMIN));
        verify(popupRepo, never()).deleteWaitingsByPopId(anyLong());
        verify(popupRepo, never()).delete(any());
    }

    @Test
    @DisplayName("삭제 성공 : 웨이팅 -> 찜 -> 팝업 순서로 삭제")
    void deletePopup_success() {
        PopupStore popup = createPopup("OPEN");
        when(accessService.checkPopupAccess(1L, ADMIN)).thenReturn(popup);

        popupService.deletePopup(1L, ADMIN);

        InOrder order = inOrder(popupRepo); // 호출 순서 확인 (FK 때문에 팝업은 마지막에 삭제)
        order.verify(popupRepo).deleteWaitingsByPopId(1L);
        order.verify(popupRepo).deleteFavoritesByPopId(1L);
        order.verify(popupRepo).delete(popup);
    }

    // 일시 중지 / 재개 / 운영 취소

    @Test
    @DisplayName("일시 중지 성공 : OPEN -> PAUSED")
    void pausePopup_success() {
        when(accessService.checkPopupAccess(1L, MANAGER)).thenReturn(createPopup("OPEN"));

        PopupStoreResponse response = popupService.pausePopup(1L, MANAGER);

        assertEquals("PAUSED", response.getOpStatus());
    }

    @Test
    @DisplayName("일시 중지 실패 : 이미 중지된 팝업이면 POPUP_ALREADY_PAUSED")
    void pausePopup_alreadyPaused() {
        when(accessService.checkPopupAccess(1L, MANAGER)).thenReturn(createPopup("PAUSED"));

        assertErrorCode(PopupErrorCode.POPUP_ALREADY_PAUSED, () -> popupService.pausePopup(1L, MANAGER));
    }

    @Test
    @DisplayName("재개 성공 : PAUSED -> OPEN")
    void resumePopup_success() {
        when(accessService.checkPopupAccess(1L, MANAGER)).thenReturn(createPopup("PAUSED"));

        PopupStoreResponse response = popupService.resumePopup(1L, MANAGER);

        assertEquals("OPEN", response.getOpStatus());
    }

    @Test
    @DisplayName("재개 실패 : 중지 상태가 아니면 POPUP_NOT_PAUSED")
    void resumePopup_notPaused() {
        when(accessService.checkPopupAccess(1L, MANAGER)).thenReturn(createPopup("OPEN"));

        assertErrorCode(PopupErrorCode.POPUP_NOT_PAUSED, () -> popupService.resumePopup(1L, MANAGER));
    }

    @Test
    @DisplayName("운영 취소 성공 : CANCELLED로 바뀌고 시작 전 회차·예약, 미입장 웨이팅을 함께 취소")
    void cancelPopup_success() {
        when(accessService.checkPopupAccess(1L, ADMIN)).thenReturn(createPopup("OPEN"));

        PopupStoreResponse response = popupService.cancelPopup(1L, ADMIN);

        assertEquals("CANCELLED", response.getOpStatus());
        verify(timeSlotService).cancelUpcomingSlotsByPopup(1L);
        verify(waitingQueueRepo).bulkCancelActiveWaitingsByPopup(eq(1L), anyCollection(), any());
    }

    @Test
    @DisplayName("운영 취소 실패 : MANAGER는 운영 취소할 수 없음 (ADMIN_ONLY)")
    void cancelPopup_manager() {
        doThrow(new PopupException(PopupErrorCode.ADMIN_ONLY)).when(accessService).checkAdmin(MANAGER);

        assertErrorCode(PopupErrorCode.ADMIN_ONLY, () -> popupService.cancelPopup(1L, MANAGER));
        verify(timeSlotService, never()).cancelUpcomingSlotsByPopup(anyLong());
    }

    @Test
    @DisplayName("운영 취소 실패 : 이미 취소된 팝업이면 POPUP_ALREADY_CANCELLED")
    void cancelPopup_alreadyCancelled() {
        when(accessService.checkPopupAccess(1L, ADMIN)).thenReturn(createPopup("CANCELLED"));

        assertErrorCode(PopupErrorCode.POPUP_ALREADY_CANCELLED, () -> popupService.cancelPopup(1L, ADMIN));
        verify(timeSlotService, never()).cancelUpcomingSlotsByPopup(anyLong());
    }

    // 목록 검색·필터

    @Test
    @DisplayName("검색 : 검색어 앞뒤 공백 제거, 진행 상태는 대문자로 바꿔서 조회")
    void listUserPopups_cleanParams() {
        when(popupRepo.findUserPopups(any(), any(), any(), any(), any())).thenReturn(List.of());

        List<PopupStoreResponse> result = popupService.listUserPopups(null, "  성수 ", "ongoing", "FOOD", null);

        assertTrue(result.isEmpty());
        verify(popupRepo).findUserPopups(eq("성수"), eq("ONGOING"), eq("FOOD"), isNull(), any(LocalDate.class));
    }

    @Test
    @DisplayName("검색 실패 : 허용 목록에 없는 카테고리면 INVALID_CATEGORY")
    void listUserPopups_invalidCategory() {
        assertErrorCode(PopupErrorCode.INVALID_CATEGORY,
                () -> popupService.listUserPopups(null, null, null, "ENTERTAINMENT", null));
    }

    @Test
    @DisplayName("검색 실패 : 없는 진행 상태면 INVALID_POPUP_STATUS")
    void listUserPopups_invalidStatus() {
        assertErrorCode(PopupErrorCode.INVALID_POPUP_STATUS,
                () -> popupService.listUserPopups(null, null, "CLOSED", null, null));
    }
}

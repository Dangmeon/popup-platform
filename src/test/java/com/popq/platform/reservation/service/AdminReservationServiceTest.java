package com.popq.platform.reservation.service;

import com.popq.platform.auth.entity.User;
import com.popq.platform.popup.entity.PopupStore;
import com.popq.platform.popup.exception.PopupErrorCode;
import com.popq.platform.popup.exception.PopupException;
import com.popq.platform.popup.service.PopupAccessService;
import com.popq.platform.reservation.dto.AdminReservationResponse;
import com.popq.platform.reservation.entity.Reservation;
import com.popq.platform.reservation.entity.ReservationStatus;
import com.popq.platform.reservation.repository.ReservationRepository;

import com.popq.platform.timeslot.entity.TimeSlot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminReservationServiceTest {

    @Mock
    private PopupAccessService popupAccessService;

    @Mock
    private ReservationRepository reservationRepository;

    @InjectMocks
    private AdminReservationService adminReservationService;

    @Test
    @DisplayName("팝업 접근 권한이 없으면 예약 목록을 조회할 수 없다")
    void getReservations_accessDenied() {

        String username = "testManager";
        Long popId = 1L;

        // 팝업 접근 권한 검증 실패
        when(popupAccessService.checkPopupAccess(popId, username))
                .thenThrow(
                        new PopupException(
                                PopupErrorCode.POPUP_ACCESS_DENIED
                        )
                );

        PopupException exception = assertThrows(
                PopupException.class,
                () -> adminReservationService.getReservations(username, popId, null)
        );

        assertEquals(
                PopupErrorCode.POPUP_ACCESS_DENIED,
                exception.getErrorCode()
        );

        // 팝업 접근 권한 검증 확인
        verify(popupAccessService).checkPopupAccess(popId, username);

        verifyNoInteractions(reservationRepository);
    }

    @Test
    @DisplayName("예약 상태 필터가 없으면 전체 예약을 조회하고 DTO로 변환한다")
    void getReservations_withoutStatus() {

        String username = "testManager";
        Long popId = 1L;

        Long reservationId = 1L;
        Long timeSlotId = 2L;
        Long memberId = 3L;

        String title = "테스트 팝업";
        String memberUsername = "testUser";

        LocalDate slotDate = LocalDate.now().plusDays(1);
        String startTime = "14:00";

        int peopleCount = 2;

        LocalDateTime reservedAt = LocalDateTime.now();

        ReservationStatus status = ReservationStatus.CONFIRMED;

        Reservation reservation = mock(Reservation.class);
        TimeSlot timeSlot = mock(TimeSlot.class);
        PopupStore popupStore = mock(PopupStore.class);
        User user = mock(User.class);
        when(popupAccessService.checkPopupAccess(popId, username)).thenReturn(popupStore);
        when(reservationRepository.findByPopupId(popId)).thenReturn(List.of(reservation));

        when(reservation.getReservationId()).thenReturn(reservationId);
        when(reservation.getPeopleCount()).thenReturn(peopleCount);
        when(reservation.getReservationStatus()).thenReturn(status);
        when(reservation.getReservedAt()).thenReturn(reservedAt);
        when(reservation.getTimeSlot()).thenReturn(timeSlot);
        when(timeSlot.getTimeSlotId()).thenReturn(timeSlotId);
        when(timeSlot.getSlotDate()).thenReturn(slotDate);
        when(timeSlot.getStartTime()).thenReturn(startTime);
        when(timeSlot.getPopupStore()).thenReturn(popupStore);
        when(popupStore.getPopId()).thenReturn(popId);
        when(popupStore.getTitle()).thenReturn(title);
        when(reservation.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(memberId);
        when(user.getUsername()).thenReturn(memberUsername);

        // 상태 필터는 null로 전달
        List<AdminReservationResponse> response = adminReservationService.getReservations(username, popId, null);

        assertNotNull(response);
        assertEquals(1, response.size());

        AdminReservationResponse result = response.get(0);

        // DTO 전체 필드 검증
        assertEquals(reservationId, result.getReservationId());

        assertEquals(popId, result.getPopId());
        assertEquals(title, result.getTitle());

        assertEquals(timeSlotId, result.getTimeSlotId());
        assertEquals(slotDate, result.getSlotDate());
        assertEquals(startTime, result.getStartTime());

        assertEquals(memberId, result.getMemberId());
        assertEquals(memberUsername, result.getUsername());

        assertEquals(peopleCount, result.getPeopleCount());
        assertEquals(status, result.getReservationStatus());
        assertEquals(reservedAt, result.getReservedAt());

        // 권한 검증 및 전체 예약 조회 확인
        verify(popupAccessService).checkPopupAccess(popId, username);
        verify(reservationRepository).findByPopupId(popId);

        // 상태별 조회 메서드는 호출되지 않아야 함
        verify(reservationRepository, never()).findByPopupIdAndStatus(anyLong(), any());

        // 접근 권한 검증 이후 예약 목록을 조회하는지 확인
        InOrder inOrder = inOrder(popupAccessService, reservationRepository);

        inOrder.verify(popupAccessService).checkPopupAccess(popId, username);
        inOrder.verify(reservationRepository).findByPopupId(popId);
    }

    @Test
    @DisplayName("예약 상태 필터가 있으면 해당 상태를 조건으로 예약을 조회한다")
    void getReservations_withStatus() {

        String username = "testManager";
        Long popId = 1L;

        ReservationStatus status = ReservationStatus.CONFIRMED;

        PopupStore popupStore = mock(PopupStore.class);
        when(popupAccessService.checkPopupAccess(popId, username)).thenReturn(popupStore);
        when(reservationRepository.findByPopupIdAndStatus(popId, status)).thenReturn(List.of());

        List<AdminReservationResponse> response =
                adminReservationService.getReservations(username, popId, status);

        assertNotNull(response);
        assertTrue(response.isEmpty());

        verify(popupAccessService).checkPopupAccess(popId, username);
        verify(reservationRepository).findByPopupIdAndStatus(popId, status);
        verify(reservationRepository, never()).findByPopupId(anyLong());
    }
}
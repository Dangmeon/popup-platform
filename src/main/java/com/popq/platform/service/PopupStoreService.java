package com.popq.platform.service;

import com.popq.platform.dto.PopupStoreRequest;
import com.popq.platform.dto.PopupStoreResponse;
import com.popq.platform.entity.PopupStore;
import com.popq.platform.exception.PopupErrorCode;
import com.popq.platform.exception.PopupException;
import com.popq.platform.repository.PopupStoreRepository;
import com.popq.platform.repository.ProductRepository;
import com.popq.platform.repository.TimeSlotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class PopupStoreService {

    PopupStoreRepository popupRepo;
    TimeSlotRepository timeSlotRepo;
    ProductRepository productRepo;

    @Autowired
    public PopupStoreService(PopupStoreRepository popupRepo, TimeSlotRepository timeSlotRepo, ProductRepository productRepo) {
        this.popupRepo = popupRepo;
        this.timeSlotRepo = timeSlotRepo;
        this.productRepo = productRepo;
    }

    // 전체 팝업 조회
    public List<PopupStoreResponse> listAllPopup() {
        return toDtoList(popupRepo.findAll());
    }

    // 진행중인 팝업 조회
    public List<PopupStoreResponse> listOngoingPopup() {
        return toDtoList(popupRepo.findOngoing(LocalDate.now()));
    }

    // 카테고리별 팝업 조회
    public List<PopupStoreResponse> listPopupByCategory(String category) {
        return toDtoList(popupRepo.findByCategory(category));
    }

    // 팝업 등록 : 등록된 팝업 정보 반환
    @Transactional
    public PopupStoreResponse insertPopup(PopupStoreRequest request) {
        checkRequired(request);
        checkPeriod(request);
        checkReservTime(request);
        checkWalkinStatus(request.getWalkinStatus());
        checkNumbers(request); // 예약 정원, 오픈 차수 확인
        PopupStore saved = popupRepo.save(PopupStore.toEntity(null, request)); // popId가 null이면 insert
        return PopupStoreResponse.toDto(saved);
    }

    // 팝업 수정 : 조회한 팝업의 값을 바꾸면 트랜잭션 끝날 때 자동 UPDATE (save 호출 X) / 수정된 팝업 정보 반환
    @Transactional
    public PopupStoreResponse updatePopup(Long popId, PopupStoreRequest request) {
        PopupStore popup = popupRepo.findById(popId)
                .orElseThrow(() -> new PopupException(PopupErrorCode.POPUP_NOT_FOUND));
        checkRequired(request);
        checkPeriod(request);
        checkReservTime(request);
        checkWalkinStatus(request.getWalkinStatus());
        checkNumbers(request); // 예약 정원, 오픈 차수 확인
        checkPeriodChange(popup, request); // 기간이 바뀌면 회차, 상품과 충돌하는지 확인
        checkReservTimeChange(popup, request); // 예약 시간이 바뀌면 회차와 충돌하는지 확인
        popup.update(request);
        return PopupStoreResponse.toDto(popup);
    }

    // 웨이팅 상태 변경 : 변경된 팝업 정보 반환
    @Transactional
    public PopupStoreResponse changeWalkinStatus(Long popId, String walkinStatus) {
        PopupStore popup = popupRepo.findById(popId)
                .orElseThrow(() -> new PopupException(PopupErrorCode.POPUP_NOT_FOUND));
        checkWalkinStatus(walkinStatus);
        popup.changeWalkinStatus(walkinStatus);
        return PopupStoreResponse.toDto(popup);
    }

    // 팝업 삭제
    @Transactional
    public void deletePopup(Long popId) {
        PopupStore popup = popupRepo.findById(popId).orElseThrow(() -> new PopupException(PopupErrorCode.POPUP_NOT_FOUND));
        // 회차가 등록된 팝업은 FK 때문에 삭제 불가 -> 500 대신 409 메시지로 안내 / 없는 팝업이면 404
        if (timeSlotRepo.existsByPopId(popId)) {
            throw new PopupException(PopupErrorCode.POPUP_HAS_TIMESLOTS);
        }
        if (productRepo.existsByPopId(popId)) {
            throw new PopupException(PopupErrorCode.POPUP_HAS_PRODUCTS);
        }
        popupRepo.delete(popup);
    }

    // 팝업 상세 조회
    public PopupStoreResponse detailViewPopup(Long popId) {
        // select * from popup_store where pop_id = popId
        PopupStore entity = popupRepo.findById(popId)
                .orElseThrow(() -> new PopupException(PopupErrorCode.POPUP_NOT_FOUND));
        return PopupStoreResponse.toDto(entity);
    }

    // entity 목록 -> response 목록 변환
    private List<PopupStoreResponse> toDtoList(List<PopupStore> entityList) {
        List<PopupStoreResponse> dtoList = new ArrayList<PopupStoreResponse>();
        for (PopupStore e : entityList) {
            dtoList.add(PopupStoreResponse.toDto(e));
        }
        return dtoList;
    }

    // 필수값 체크 : 회차 생성에서 운영기간을 사용하므로 시작일, 종료일은 반드시 필요
    private void checkRequired(PopupStoreRequest request) {
        if (request.getTitle() == null || request.getTitle().isBlank()
                || request.getLocation() == null || request.getLocation().isBlank()
                || request.getStartDate() == null || request.getEndDate() == null) {
            throw new PopupException(PopupErrorCode.POPUP_REQUIRED_FIELD);
        }
    }

    // 웨이팅 상태 체크 : OPEN / PAUSED / CLOSED 만 가능 (비우면 등록은 OPEN, 수정은 기존 값 유지)
    private void checkWalkinStatus(String walkinStatus) {
        if (walkinStatus != null && !walkinStatus.equals("OPEN")
                && !walkinStatus.equals("PAUSED") && !walkinStatus.equals("CLOSED")) {
            throw new PopupException(PopupErrorCode.INVALID_WALKIN_STATUS);
        }
    }

    // 숫자 체크 : 예약 정원(reservPeopleCap), 오픈 차수(roundNum)는 값이 있으면 1 이상 (비우면 허용)
    private void checkNumbers(PopupStoreRequest request) {
        if (request.getReservPeopleCap() != null && request.getReservPeopleCap() < 1) {
            throw new PopupException(PopupErrorCode.INVALID_RESERV_PEOPLE_CAP);
        }
        if (request.getRoundNum() != null && request.getRoundNum() < 1) {
            throw new PopupException(PopupErrorCode.INVALID_ROUND_NUM);
        }
    }

    // 운영기간 체크 : 종료일이 시작일보다 빠르면 안 됨
    private void checkPeriod(PopupStoreRequest request) {
        if (request.getStartDate() != null && request.getEndDate() != null
                && request.getStartDate().isAfter(request.getEndDate())) {
            throw new PopupException(PopupErrorCode.INVALID_POPUP_PERIOD);
        }
    }

    // 운영기간 변경 체크 : 시작일이나 종료일이 바뀐 경우에만 확인
    // 1. 새 기간 밖에 회차가 남으면 안 됨
    // 2. 상품 판매는 팝업 종료일 다음날부터라서, 새 종료일 당일이나 그 전에 판매 시작하는 상품이 있으면 안 됨
    private void checkPeriodChange(PopupStore popup, PopupStoreRequest request) {
        boolean startChanged = !request.getStartDate().equals(popup.getStartDate());
        boolean endChanged = !request.getEndDate().equals(popup.getEndDate());
        if (!startChanged && !endChanged) {
            return;
        }

        Long popId = popup.getPopId();
        if (timeSlotRepo.existsBeforeStartDate(popId, request.getStartDate())
                || timeSlotRepo.existsAfterEndDate(popId, request.getEndDate())) {
            throw new PopupException(PopupErrorCode.POPUP_TIMESLOT_OUT_OF_PERIOD);
        }

        if (endChanged) {
            LocalDateTime nextDay = request.getEndDate().plusDays(1).atStartOfDay(); // 새 종료일 다음날 00:00
            if (productRepo.existsSaleStartBefore(popId, nextDay)) {
                throw new PopupException(PopupErrorCode.POPUP_PRODUCT_SALE_CONFLICT);
            }
        }
    }

    // 예약 시간 변경 체크 : 예약 시작·종료 시간이 바뀐 경우에만 확인
    // 새 예약 시간보다 일찍 시작하거나 늦게 끝나는 회차가 남으면 안 됨 (비우면 제한 없음)
    private void checkReservTimeChange(PopupStore popup, PopupStoreRequest request) {
        Long popId = popup.getPopId();
        String newStart = request.getReservStart();
        String newEnd = request.getReservEnd();

        if (newStart != null && !newStart.equals(popup.getReservStart())
                && timeSlotRepo.existsBeforeReservStart(popId, newStart)) {
            throw new PopupException(PopupErrorCode.POPUP_TIMESLOT_OUT_OF_RESERV_TIME);
        }
        if (newEnd != null && !newEnd.equals(popup.getReservEnd())
                && timeSlotRepo.existsAfterReservEnd(popId, newEnd)) {
            throw new PopupException(PopupErrorCode.POPUP_TIMESLOT_OUT_OF_RESERV_TIME);
        }
    }

    // 예약 시간 체크 : "HH:mm" 형식이어야 하고, 종료 시간이 시작 시간보다 늦어야 함
    private void checkReservTime(PopupStoreRequest request) {
        String timeRegex = "^([01]\\d|2[0-3]):[0-5]\\d$"; // 00:00 ~ 23:59
        String start = request.getReservStart();
        String end = request.getReservEnd();

        if (start != null && !start.matches(timeRegex)) {
            throw new PopupException(PopupErrorCode.INVALID_RESERV_TIME_FORMAT);
        }
        if (end != null && !end.matches(timeRegex)) {
            throw new PopupException(PopupErrorCode.INVALID_RESERV_TIME_FORMAT);
        }
        // 두 자리 형식이 보장되면 문자열 비교로 시간 순서를 판단할 수 있음
        if (start != null && end != null && start.compareTo(end) >= 0) {
            throw new PopupException(PopupErrorCode.INVALID_RESERV_TIME_RANGE);
        }
    }
}
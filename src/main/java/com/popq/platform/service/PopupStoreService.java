package com.popq.platform.service;

import com.popq.platform.dto.PopupStoreRequest;
import com.popq.platform.dto.PopupStoreResponse;
import com.popq.platform.entity.PopupStore;
import com.popq.platform.repository.PopupStoreRepository;
import com.popq.platform.repository.ProductRepository;
import com.popq.platform.repository.TimeSlotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
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
        return toDtoList(popupRepo.findByCategoryOrderByStartDateDesc(category));
    }

    // 팝업 등록 : 생성된 popId 반환
    public Long insertPopup(PopupStoreRequest request) {
        checkPeriod(request);
        checkReservTime(request);
        PopupStore saved = popupRepo.save(PopupStore.toEntity(null, request)); // popId가 null이면 insert
        return saved.getPopId();
    }

    // 팝업 수정
    public void updatePopup(Long popId, PopupStoreRequest request) {
        // DB에 없는 번호면 save()가 insert를 해버리므로 먼저 확인
        if (!popupRepo.existsById(popId)) {
            throw new IllegalArgumentException("존재하지 않는 팝업입니다. popId=" + popId);
        }
        checkPeriod(request);
        checkReservTime(request);
        popupRepo.save(PopupStore.toEntity(popId, request)); // popId가 있으면 update
    }

    // 팝업 삭제
    public void deletePopup(Long popId) {
        // 회차가 등록된 팝업은 FK 때문에 삭제 불가 -> 500 대신 400 메시지로 안내
        if (timeSlotRepo.existsByPopupStore_PopId(popId)) {
            throw new IllegalArgumentException("회차가 등록된 팝업은 삭제할 수 없습니다. 회차를 먼저 삭제해주세요.");
        }
        if (productRepo.existsByPopupStore_PopId(popId)) {
            throw new IllegalArgumentException("상품이 등록된 팝업은 삭제할 수 없습니다. 상품을 먼저 삭제해주세요.");
        }
        popupRepo.deleteById(popId);
    }

    // 팝업 상세 조회
    public PopupStoreResponse detailViewPopup(Long popId) {
        // select * from popup_store where pop_id = popId
        PopupStore entity = popupRepo.findById(popId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 팝업입니다. popId=" + popId));
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

    // 운영기간 체크 : 종료일이 시작일보다 빠르면 안 됨
    private void checkPeriod(PopupStoreRequest request) {
        if (request.getStartDate() != null && request.getEndDate() != null
                && request.getStartDate().isAfter(request.getEndDate())) {
            throw new IllegalArgumentException("종료일은 시작일보다 빠를 수 없습니다.");
        }
    }

    // 예약 시간 체크 : "HH:mm" 형식이어야 하고, 종료 시간이 시작 시간보다 늦어야 함
    private void checkReservTime(PopupStoreRequest request) {
        String timeRegex = "^([01]\\d|2[0-3]):[0-5]\\d$"; // 00:00 ~ 23:59
        String start = request.getReservStart();
        String end = request.getReservEnd();

        if (start != null && !start.matches(timeRegex)) {
            throw new IllegalArgumentException("예약 시작 시간은 HH:mm 형식이어야 합니다. 예) 10:00");
        }
        if (end != null && !end.matches(timeRegex)) {
            throw new IllegalArgumentException("예약 종료 시간은 HH:mm 형식이어야 합니다. 예) 20:00");
        }
        // 두 자리 형식이 보장되면 문자열 비교로 시간 순서를 판단할 수 있음
        if (start != null && end != null && start.compareTo(end) >= 0) {
            throw new IllegalArgumentException("예약 종료 시간은 시작 시간보다 늦어야 합니다.");
        }
    }
}

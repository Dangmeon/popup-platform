package com.popq.platform.service;

import com.popq.platform.dto.PopupStoreDTO;
import com.popq.platform.entity.PopupStoreEntity;
import com.popq.platform.repository.PopupStoreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class PopupStoreService implements IPopupStoreService {

    PopupStoreRepository popupRepo;

    @Autowired
    public PopupStoreService(PopupStoreRepository popupRepo) {
        this.popupRepo = popupRepo;
    }

    @Override
    public List<PopupStoreDTO> listAllPopup() {
        // select * from popup_store
        return toDtoList(popupRepo.findAll());
    }

    @Override
    public List<PopupStoreDTO> listOngoingPopup() {
        return toDtoList(popupRepo.findOngoing(LocalDate.now()));
    }

    @Override
    public List<PopupStoreDTO> listPopupByCategory(String category) {
        return toDtoList(popupRepo.findByCategoryOrderByStartDateDesc(category));
    }

    @Override
    public Long insertPopup(PopupStoreDTO dto) {
        checkPeriod(dto);
        checkReservTime(dto);
        dto.setPopId(null); // 등록할 때는 popId를 시퀀스가 만들도록 비워둠
        PopupStoreEntity saved = popupRepo.save(PopupStoreEntity.toEntity(dto)); // popId가 null이면 insert
        return saved.getPopId();
    }

    @Override
    public void updatePopup(PopupStoreDTO dto) {
        // popId가 없거나 DB에 없는 번호면 save()가 insert를 해버리므로 먼저 확인
        if (dto.getPopId() == null || !popupRepo.existsById(dto.getPopId())) {
            throw new IllegalArgumentException("존재하지 않는 팝업입니다. popId=" + dto.getPopId());
        }
        checkPeriod(dto);
        checkReservTime(dto);
        popupRepo.save(PopupStoreEntity.toEntity(dto)); // popId가 있으면 update
    }

    @Override
    public void deletePopup(Long popId) {
        popupRepo.deleteById(popId);
    }

    @Override
    public PopupStoreDTO detailViewPopup(Long popId) {
        // select * from popup_store where pop_id = popId
        PopupStoreEntity entity = popupRepo.findById(popId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 팝업입니다. popId=" + popId));
        return PopupStoreDTO.toDto(entity);
    }

    // entity 목록 -> dto 목록 변환
    private List<PopupStoreDTO> toDtoList(List<PopupStoreEntity> entityList) {
        List<PopupStoreDTO> dtoList = new ArrayList<PopupStoreDTO>();
        for (PopupStoreEntity e : entityList) {
            dtoList.add(PopupStoreDTO.toDto(e));
        }
        return dtoList;
    }

    // 운영기간 체크 : 종료일이 시작일보다 빠르면 안 됨
    private void checkPeriod(PopupStoreDTO dto) {
        if (dto.getStartDate() != null && dto.getEndDate() != null
                && dto.getStartDate().isAfter(dto.getEndDate())) {
            throw new IllegalArgumentException("종료일은 시작일보다 빠를 수 없습니다.");
        }
    }

    // 예약 시간 체크 : "HH:mm" 형식이어야 하고, 종료 시간이 시작 시간보다 늦어야 함
    private void checkReservTime(PopupStoreDTO dto) {
        String timeRegex = "^([01]\\d|2[0-3]):[0-5]\\d$"; // 00:00 ~ 23:59
        String start = dto.getReservStart();
        String end = dto.getReservEnd();

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

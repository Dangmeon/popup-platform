package com.popq.platform.service;

import com.popq.platform.dto.PopupStoreDTO;

import java.util.List;

public interface IPopupStoreService {
    // 전체 팝업 조회
    public List<PopupStoreDTO> listAllPopup();

    // 진행중인 팝업 조회
    public List<PopupStoreDTO> listOngoingPopup();

    // 카테고리별 팝업 조회
    public List<PopupStoreDTO> listPopupByCategory(String category);

    // 팝업 등록 : 생성된 popId 반환
    public Long insertPopup(PopupStoreDTO dto);

    // 팝업 수정
    public void updatePopup(PopupStoreDTO dto);

    // 팝업 삭제
    public void deletePopup(Long popId);

    // 팝업 상세 조회
    public PopupStoreDTO detailViewPopup(Long popId);
}

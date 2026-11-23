package com.popq.platform.favorite.service;

import com.popq.platform.favorite.dto.FavoriteResponse;
import com.popq.platform.favorite.entity.Favorite;
import com.popq.platform.entity.PopupStore;
import com.popq.platform.entity.User;
import com.popq.platform.exception.PopupErrorCode;
import com.popq.platform.exception.PopupException;
import com.popq.platform.favorite.repository.FavoriteRepository;
import com.popq.platform.repository.PopupStoreRepository;
import com.popq.platform.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class FavoriteService {

    FavoriteRepository favoriteRepo;
    PopupStoreRepository popupRepo;
    UserRepository userRepo;

    @Autowired
    public FavoriteService(FavoriteRepository favoriteRepo,
                           PopupStoreRepository popupRepo,
                           UserRepository userRepo) {
        this.favoriteRepo = favoriteRepo;
        this.popupRepo = popupRepo;
        this.userRepo = userRepo;
    }

    /**
     * 팝업스토어 찜 등록
     * - [멱등성 보장]
     *   사용자가 찜 버튼을 실수로 연속 두 번 눌렀을 때 에러(500)를 내거나 중복 데이터를 넣지 않고,
     *   이미 찜한 내역이 있으면 기존 favoriteId와 isFavorite=true를 그대로 보여줌
     */
    @Transactional // DB - INSERT
    public FavoriteResponse addPopupFavorite(Long popId, String username) {
        User member = getLoginUser(username); // 로그인 사용자 검증
        PopupStore popup = findActivePopup(popId); // 존재하는 정상 팝업인지 검증

        return favoriteRepo.findPopupFavorite(member.getId(), popId)
                // 이미 찜한 기록이 있으면 새로 저장하지 않고 기존 데이터 반환
                .map(existing -> FavoriteResponse.of(existing.getFavoriteId(), popId, true))
                // 찜한 기록이 없을 때만 새로 INSERT 후 반환
                .orElseGet(() -> {
                    Favorite saved = favoriteRepo.save(Favorite.createPopupFavorite(member, popup));
                    return FavoriteResponse.of(saved.getFavoriteId(), popId, true);
                });
    }

    /**
     * 팝업스토어 찜 취소
     * - 이미 취소된 상태에서 다시 DELETE 요청이 와도 에러 없이 isFavorite=false를 반환 처리
     */
    @Transactional // DB - DELETE
    public FavoriteResponse removePopupFavorite(Long popId, String username) {
        User member = getLoginUser(username);
        findActivePopup(popId); // 존재하지 않거나 종료된 팝업이면 404 POPUP_NOT_FOUND

        // 찜 기록이 존재할 경우에만 삭제 실행
        favoriteRepo.findPopupFavorite(member.getId(), popId)
                .ifPresent(favoriteRepo::delete);

        return FavoriteResponse.of(null, popId, false);
    }

    // SecurityContext에서 넘어온 username(이메일)으로 실제 회원 엔티티 조회
    private User getLoginUser(String username) {
        // 비로그인 상태일 경우 Spring Security가 null 또는 "anonymousUser" 문자열을 넘겨주므로 차단
        if (username == null || username.isBlank() || "anonymousUser".equals(username)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return userRepo.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));
    }

    // 팝업 존재 여부 및 영구 종료(CANCELLED) 상태 차단
    private PopupStore findActivePopup(Long popId) {
        PopupStore popup = popupRepo.findById(popId)
                .orElseThrow(() -> new PopupException(PopupErrorCode.POPUP_NOT_FOUND));
        // 관리자가 영구 종료시킨 팝업은 일반 사용자에게 노출되지 않으므로 404 처리
        if ("CANCELLED".equals(popup.getOpStatus())) {
            throw new PopupException(PopupErrorCode.POPUP_NOT_FOUND);
        }
        return popup;
    }
}
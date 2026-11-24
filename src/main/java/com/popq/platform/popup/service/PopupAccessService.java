package com.popq.platform.popup.service;

import com.popq.platform.popup.dto.ManagerResponse;
import com.popq.platform.popup.entity.PopupStore;
import com.popq.platform.auth.entity.Role;
import com.popq.platform.auth.entity.User;
import com.popq.platform.popup.exception.PopupErrorCode;
import com.popq.platform.popup.exception.PopupException;
import com.popq.platform.popup.repository.PopupStoreRepository;
import com.popq.platform.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

// 관리자 API 공통 권한 검사
// ADMIN : 모든 팝업 관리 가능 / MANAGER : 본인이 담당(manager_id)인 팝업만 관리 가능
@Service
@Transactional(readOnly = true)
public class PopupAccessService {
    PopupStoreRepository popupRepo;
    UserRepository userRepo;

    @Autowired
    public PopupAccessService(PopupStoreRepository popupRepo, UserRepository userRepo) {
        this.popupRepo = popupRepo;
        this.userRepo = userRepo;
    }

    // 로그인한 회원 조회 (username = JWT에 들어있는 이메일)
    public User getLoginUser(String username) {
        if (username == null) {
            throw new PopupException(PopupErrorCode.POPUP_ACCESS_DENIED);
        }
        return userRepo.findByUsername(username)
                .orElseThrow(() -> new PopupException(PopupErrorCode.POPUP_ACCESS_DENIED));
    }

    // 팝업 관리 권한 확인 : 통과하면 팝업을 돌려줌 / 없는 팝업 404, 권한 없음 403
    public PopupStore checkPopupAccess(Long popId, String username) {
        PopupStore popup = popupRepo.findById(popId)
                .orElseThrow(() -> new PopupException(PopupErrorCode.POPUP_NOT_FOUND));
        checkAccess(popup, getLoginUser(username));
        return popup;
    }

    // 이미 조회한 팝업으로 권한 확인 (회차, 상품에서 꺼낸 팝업)
    public void checkAccess(PopupStore popup, User loginUser) {
        if (loginUser.getRole() == Role.ADMIN) {
            return;
        }
        if (loginUser.getRole() == Role.MANAGER
                && popup.getManager() != null
                && popup.getManager().getId().equals(loginUser.getId())) {
            return;
        }
        throw new PopupException(PopupErrorCode.POPUP_ACCESS_DENIED);
    }

    // ADMIN 전용 기능 확인 (담당 매니저 배정 등) : ADMIN이 아니면 403
    public void checkAdmin(String username) {
        if (getLoginUser(username).getRole() != Role.ADMIN) {
            throw new PopupException(PopupErrorCode.ADMIN_ONLY);
        }
    }

    // 담당자로 지정할 매니저 조회 : 없는 이메일 404, MANAGER가 아니면 400
    public User getManagerByEmail(String email) {
        User manager = userRepo.findByUsername(email.trim())
                .orElseThrow(() -> new PopupException(PopupErrorCode.MANAGER_NOT_FOUND));
        if (manager.getRole() != Role.MANAGER) {
            throw new PopupException(PopupErrorCode.NOT_MANAGER_ROLE);
        }
        return manager;
    }

    // 매니저 목록 (ADMIN 전용, 배정 드롭다운용) : 활동 중인 MANAGER 회원만, 이름순
    public List<ManagerResponse> listManagers(String username) {
        checkAdmin(username); // ADMIN이 아니면 403
        List<ManagerResponse> dtoList = new ArrayList<ManagerResponse>();
        for (User u : userRepo.findByRoleAndStatus(Role.MANAGER, User.UserStatus.ACTIVE)) {
            dtoList.add(ManagerResponse.toDto(u));
        }
        return dtoList;
    }

    // 관리자 팝업 목록에서 사용 : MANAGER면 본인 id, ADMIN이면 null(전체)
    public Long getManagerIdForList(String username) {
        User loginUser = getLoginUser(username);
        if (loginUser.getRole() == Role.ADMIN) {
            return null;
        }
        if (loginUser.getRole() == Role.MANAGER) {
            return loginUser.getId();
        }
        throw new PopupException(PopupErrorCode.POPUP_ACCESS_DENIED);
    }
}

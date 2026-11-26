package com.popq.platform.popup;

import com.popq.platform.auth.entity.Role;
import com.popq.platform.auth.entity.User;
import com.popq.platform.auth.repository.UserRepository;
import com.popq.platform.popup.dto.ManagerResponse;
import com.popq.platform.popup.entity.PopupStore;
import com.popq.platform.popup.exception.PopupErrorCode;
import com.popq.platform.popup.exception.PopupException;
import com.popq.platform.popup.repository.PopupStoreRepository;
import com.popq.platform.popup.service.PopupAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// PopupAccessService 단위 테스트 : 관리자 API 권한 판단 로직 (ADMIN 전체 / MANAGER 담당 팝업만 / USER 불가)
@ExtendWith(MockitoExtension.class)
public class PopupAccessServiceTest {

    @Mock
    PopupStoreRepository popupRepo;
    @Mock
    UserRepository userRepo;

    PopupAccessService accessService;

    User admin;
    User manager;      // 담당 매니저 (id 2)
    User otherManager; // 다른 매니저 (id 3)
    User user;         // 일반 회원 (id 4)

    @BeforeEach
    void setUp() {
        accessService = new PopupAccessService(popupRepo, userRepo);
        admin = createUser(1L, "admin@gmail.com", Role.ADMIN);
        manager = createUser(2L, "manager@gmail.com", Role.MANAGER);
        otherManager = createUser(3L, "other@gmail.com", Role.MANAGER);
        user = createUser(4L, "user@gmail.com", Role.USER);
    }

    // 회원 생성 : id는 DB 시퀀스가 만드는 값이라 setter가 없어서 ReflectionTestUtils로 직접 넣음
    private User createUser(Long id, String email, Role role) {
        User u = User.builder().username(email).name("테스트").phone("010-0000-0000").role(role).build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    // 담당 매니저가 지정된 팝업 (매니저가 없으면 null)
    private PopupStore createPopup(User popupManager) {
        PopupStore popup = PopupStore.builder().popId(1L).title("테스트 팝업").build();
        popup.assignManager(popupManager);
        return popup;
    }

    // 예외가 나는지, 어떤 에러 코드인지 확인
    private void assertErrorCode(PopupErrorCode expected, org.junit.jupiter.api.function.Executable action) {
        PopupException e = assertThrows(PopupException.class, action);
        assertEquals(expected, e.getErrorCode());
    }

    // 로그인 회원 조회

    @Test
    @DisplayName("로그인 회원 조회 실패 : 비로그인(username null)이면 POPUP_ACCESS_DENIED")
    void getLoginUser_null() {
        assertErrorCode(PopupErrorCode.POPUP_ACCESS_DENIED, () -> accessService.getLoginUser(null));
    }

    @Test
    @DisplayName("로그인 회원 조회 실패 : 없는 회원이면 POPUP_ACCESS_DENIED")
    void getLoginUser_notFound() {
        when(userRepo.findByUsername("none@gmail.com")).thenReturn(Optional.empty());

        assertErrorCode(PopupErrorCode.POPUP_ACCESS_DENIED, () -> accessService.getLoginUser("none@gmail.com"));
    }

    // 팝업 관리 권한

    @Test
    @DisplayName("권한 통과 : ADMIN은 담당이 아닌 팝업도 관리 가능")
    void checkAccess_admin() {
        assertDoesNotThrow(() -> accessService.checkAccess(createPopup(manager), admin));
        assertDoesNotThrow(() -> accessService.checkAccess(createPopup(null), admin)); // 매니저 없는 팝업도 가능
    }

    @Test
    @DisplayName("권한 통과 : MANAGER는 본인이 담당인 팝업 관리 가능")
    void checkAccess_ownManager() {
        assertDoesNotThrow(() -> accessService.checkAccess(createPopup(manager), manager));
    }

    @Test
    @DisplayName("권한 실패 : MANAGER가 다른 매니저 팝업을 관리하면 POPUP_ACCESS_DENIED")
    void checkAccess_otherManager() {
        assertErrorCode(PopupErrorCode.POPUP_ACCESS_DENIED,
                () -> accessService.checkAccess(createPopup(manager), otherManager));
    }

    @Test
    @DisplayName("권한 실패 : 담당 매니저가 없는 팝업은 MANAGER가 관리할 수 없음")
    void checkAccess_noManager() {
        assertErrorCode(PopupErrorCode.POPUP_ACCESS_DENIED,
                () -> accessService.checkAccess(createPopup(null), manager));
    }

    @Test
    @DisplayName("권한 실패 : 일반 회원(USER)은 관리할 수 없음")
    void checkAccess_user() {
        assertErrorCode(PopupErrorCode.POPUP_ACCESS_DENIED,
                () -> accessService.checkAccess(createPopup(manager), user));
    }

    @Test
    @DisplayName("팝업 권한 확인 : 통과하면 조회한 팝업을 돌려줌")
    void checkPopupAccess_success() {
        PopupStore popup = createPopup(manager);
        when(popupRepo.findById(1L)).thenReturn(Optional.of(popup));
        when(userRepo.findByUsername("manager@gmail.com")).thenReturn(Optional.of(manager));

        PopupStore result = accessService.checkPopupAccess(1L, "manager@gmail.com");

        assertSame(popup, result);
    }

    @Test
    @DisplayName("팝업 권한 확인 실패 : 없는 팝업이면 POPUP_NOT_FOUND (권한보다 먼저 확인)")
    void checkPopupAccess_notFound() {
        when(popupRepo.findById(99L)).thenReturn(Optional.empty());

        assertErrorCode(PopupErrorCode.POPUP_NOT_FOUND, () -> accessService.checkPopupAccess(99L, "manager@gmail.com"));
        verify(userRepo, never()).findByUsername(anyString());
    }

    // ADMIN 전용

    @Test
    @DisplayName("ADMIN 전용 통과 : ADMIN이면 예외 없음")
    void checkAdmin_admin() {
        when(userRepo.findByUsername("admin@gmail.com")).thenReturn(Optional.of(admin));

        assertDoesNotThrow(() -> accessService.checkAdmin("admin@gmail.com"));
    }

    @Test
    @DisplayName("ADMIN 전용 실패 : MANAGER면 ADMIN_ONLY")
    void checkAdmin_manager() {
        when(userRepo.findByUsername("manager@gmail.com")).thenReturn(Optional.of(manager));

        assertErrorCode(PopupErrorCode.ADMIN_ONLY, () -> accessService.checkAdmin("manager@gmail.com"));
    }

    // 담당 매니저 지정

    @Test
    @DisplayName("매니저 조회 성공 : 이메일 앞뒤 공백을 지우고 조회")
    void getManagerByEmail_success() {
        when(userRepo.findByUsername("manager@gmail.com")).thenReturn(Optional.of(manager));

        User result = accessService.getManagerByEmail("  manager@gmail.com ");

        assertSame(manager, result);
    }

    @Test
    @DisplayName("매니저 조회 실패 : 없는 이메일이면 MANAGER_NOT_FOUND")
    void getManagerByEmail_notFound() {
        when(userRepo.findByUsername("none@gmail.com")).thenReturn(Optional.empty());

        assertErrorCode(PopupErrorCode.MANAGER_NOT_FOUND, () -> accessService.getManagerByEmail("none@gmail.com"));
    }

    @Test
    @DisplayName("매니저 조회 실패 : MANAGER가 아닌 회원이면 NOT_MANAGER_ROLE")
    void getManagerByEmail_notManager() {
        when(userRepo.findByUsername("user@gmail.com")).thenReturn(Optional.of(user));

        assertErrorCode(PopupErrorCode.NOT_MANAGER_ROLE, () -> accessService.getManagerByEmail("user@gmail.com"));
    }

    @Test
    @DisplayName("매니저 목록 : ADMIN이면 활동 중인 MANAGER 목록 반환")
    void listManagers_admin() {
        when(userRepo.findByUsername("admin@gmail.com")).thenReturn(Optional.of(admin));
        when(userRepo.findByRoleAndStatus(Role.MANAGER, User.UserStatus.ACTIVE)).thenReturn(List.of(manager, otherManager));

        List<ManagerResponse> result = accessService.listManagers("admin@gmail.com");

        assertEquals(2, result.size());
        assertEquals("manager@gmail.com", result.get(0).getEmail());
    }

    @Test
    @DisplayName("매니저 목록 실패 : MANAGER면 ADMIN_ONLY")
    void listManagers_manager() {
        when(userRepo.findByUsername("manager@gmail.com")).thenReturn(Optional.of(manager));

        assertErrorCode(PopupErrorCode.ADMIN_ONLY, () -> accessService.listManagers("manager@gmail.com"));
        verify(userRepo, never()).findByRoleAndStatus(any(), any());
    }

    // 관리자 팝업 목록

    @Test
    @DisplayName("관리자 목록 : ADMIN이면 null(전체), MANAGER면 본인 id")
    void getManagerIdForList() {
        when(userRepo.findByUsername("admin@gmail.com")).thenReturn(Optional.of(admin));
        when(userRepo.findByUsername("manager@gmail.com")).thenReturn(Optional.of(manager));

        assertNull(accessService.getManagerIdForList("admin@gmail.com"));
        assertEquals(2L, accessService.getManagerIdForList("manager@gmail.com"));
    }

    @Test
    @DisplayName("관리자 목록 실패 : 일반 회원(USER)이면 POPUP_ACCESS_DENIED")
    void getManagerIdForList_user() {
        when(userRepo.findByUsername("user@gmail.com")).thenReturn(Optional.of(user));

        assertErrorCode(PopupErrorCode.POPUP_ACCESS_DENIED, () -> accessService.getManagerIdForList("user@gmail.com"));
    }
}

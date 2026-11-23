package com.popq.platform.service;

import com.popq.platform.dto.ManagerResponse;
import com.popq.platform.dto.PopupManagerRequest;
import com.popq.platform.dto.PopupStoreRequest;
import com.popq.platform.dto.PopupStoreResponse;
import com.popq.platform.entity.PopupStore;
import com.popq.platform.auth.entity.Role;
import com.popq.platform.auth.entity.User;
import com.popq.platform.exception.PopupErrorCode;
import com.popq.platform.exception.PopupException;
import com.popq.platform.favorite.repository.FavoriteRepository;
import com.popq.platform.repository.PopupStoreRepository;
import com.popq.platform.repository.ProductRepository;
import com.popq.platform.repository.TimeSlotRepository;
import com.popq.platform.repository.UserRepository;
import com.popq.platform.waiting.entity.WaitingStatus;
import com.popq.platform.waiting.repository.WaitingQueueRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class PopupStoreService {

    PopupStoreRepository popupRepo;
    TimeSlotRepository timeSlotRepo;
    // -- 대기열 리포 추가
    WaitingQueueRepository waitingQueueRepo;
    FavoriteRepository favoriteRepo;
    UserRepository userRepo;

    ProductRepository productRepo;
    PopupAccessService accessService;
    // -- 타임슬롯 추가
    TimeSlotService timeSlotService;

    @Autowired
    public PopupStoreService(PopupStoreRepository popupRepo, TimeSlotRepository timeSlotRepo,
                             ProductRepository productRepo, PopupAccessService accessService,
                            TimeSlotService timeSlotService, WaitingQueueRepository waitingQueueRepo,
                             FavoriteRepository favoriteRepo,
                             UserRepository userRepo) {
        this.popupRepo = popupRepo;
        this.timeSlotRepo = timeSlotRepo;
        this.productRepo = productRepo;
        this.accessService = accessService;
        this.favoriteRepo = favoriteRepo;
        this.timeSlotService = timeSlotService;
        this.waitingQueueRepo = waitingQueueRepo;
        this.userRepo = userRepo;
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

    // 사용자 팝업 목록 통합 조회 (status / category / sort=POPULAR + 로그인 회원 찜 여부 / 총 찜 수)
    public List<PopupStoreResponse> listUserPopups(String username, String status, String category, String sort) {
        String cleanStatus = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;
        String cleanCategory = (category != null && !category.isBlank()) ? category.trim() : null;
        String cleanSort = (sort != null && !sort.isBlank()) ? sort.trim().toUpperCase() : null;

        List<PopupStore> popups = popupRepo.findUserPopups(cleanStatus, cleanCategory, cleanSort, LocalDate.now());
        if (popups.isEmpty()) {
            return List.of();
        }

        // 팝업 ID 목록 추출
        List<Long> popIds = popups.stream().map(PopupStore::getPopId).toList();

        // 각 팝업별 총 찜 개수 일괄 조회 (N+1 방지)
        Map<Long, Long> favoriteCountMap = new HashMap<>();
        for (Object[] row : favoriteRepo.countFavoritesByPopIds(popIds)) {
            favoriteCountMap.put((Long) row[0], (Long) row[1]);
        }

        // 로그인한 회원이면 내가 찜한 popId 목록 일괄 조회 (비로그인이면 빈 Set)
        Set<Long> myFavoritedPopIds = resolveLoginMemberId(username)
                .map(memberId -> favoriteRepo.findFavoritedPopIds(memberId, popIds))
                .orElse(Collections.emptySet());

        // DTO 조립
        List<PopupStoreResponse> result = new ArrayList<>();
        for (PopupStore p : popups) {
            boolean isFav = myFavoritedPopIds.contains(p.getPopId());
            long favCount = favoriteCountMap.getOrDefault(p.getPopId(), 0L);
            result.add(PopupStoreResponse.toDto(p, isFav, favCount));
        }
        return result;
    }

    // 관리자 팝업 목록 : ADMIN은 전체, MANAGER는 본인 팝업만 / keyword(팝업명·장소), opStatus 필터, 시작일 최신순
    public List<PopupStoreResponse> listAdminPopup(String username, String keyword, String opStatus) {
        Long managerId = accessService.getManagerIdForList(username);
        if (keyword == null || keyword.isBlank()) { // 빈 검색어는 검색 안 함
            keyword = null;
        } else {
            keyword = keyword.trim();
        }
        if (opStatus != null && opStatus.isBlank()) {
            opStatus = null;
        }
        return toDtoList(popupRepo.findAdminList(managerId, keyword, opStatus));
    }

    // 팝업 등록 : 등록된 팝업 정보 반환
    @Transactional
    public PopupStoreResponse insertPopup(PopupStoreRequest request, String username) {
        checkRequired(request);
        checkPeriod(request);
        checkReservTime(request);
        checkNumbers(request); // 예약 정원, 오픈 차수 확인
        PopupStore popup = PopupStore.toEntity(null, request);
        User loginUser = accessService.getLoginUser(username);
        if (loginUser.getRole() == Role.MANAGER) {
            popup.assignManager(loginUser);
        }
        PopupStore saved = popupRepo.save(popup); // popId가 null이면 insert
        return PopupStoreResponse.toDto(saved);
    }

    // 팝업 수정 : 조회한 팝업의 값을 바꾸면 트랜잭션 끝날 때 자동 UPDATE (save 호출 X) / 수정된 팝업 정보 반환
    @Transactional
    public PopupStoreResponse updatePopup(Long popId, PopupStoreRequest request, String username) {
        PopupStore popup = accessService.checkPopupAccess(popId, username); // 없으면 404, 담당이 아니면 403

        popup.validateNotCancelled(); // 취소된 팝업은 수정 불가

        checkRequired(request);
        checkPeriod(request);
        checkReservTime(request);
        checkNumbers(request); // 예약 정원, 오픈 차수 확인
        checkPeriodChange(popup, request); // 기간이 바뀌면 회차, 상품과 충돌하는지 확인
        checkReservTimeChange(popup, request); // 예약 시간이 바뀌면 회차와 충돌하는지 확인
        popup.update(request);
        return PopupStoreResponse.toDto(popup);
    }

    // 팝업 삭제
    @Transactional
    public void deletePopup(Long popId, String username) {
        PopupStore popup = accessService.checkPopupAccess(popId, username); // 없으면 404, 담당이 아니면 403
        // 회차가 등록된 팝업은 FK 때문에 삭제 불가 -> 500 대신 409 메시지로 안내 / 없는 팝업이면 404
        if (timeSlotRepo.existsByPopId(popId)) {
            throw new PopupException(PopupErrorCode.POPUP_HAS_TIMESLOTS);
        }
        if (productRepo.existsByPopId(popId)) {
            throw new PopupException(PopupErrorCode.POPUP_HAS_PRODUCTS);
        }
        popupRepo.delete(popup);
    }

    // 팀장 작성 ------------------------- 팝업 중지/재개/종료 메소드 ------------------------

    // 팝업 중지 (OPEN -> PAUSED)
    @Transactional
    public PopupStoreResponse pausePopup(Long popId, String username) {
        PopupStore popup = accessService.checkPopupAccess(popId, username);
        popup.pause();
        return PopupStoreResponse.toDto(popup);
    }

    // 팝업 재개 (PAUSED -> OPEN)
    @Transactional
    public PopupStoreResponse resumePopup(Long popId, String username) {
        PopupStore popup = accessService.checkPopupAccess(popId, username);
        popup.resume();
        return PopupStoreResponse.toDto(popup);
    }

    // 팝업 종료 (복구 불가) : 시작 전 회차·예약·웨이팅 일괄 취소
    @Transactional
    public PopupStoreResponse cancelPopup(Long popId, String username) {
        PopupStore popup = accessService.checkPopupAccess(popId, username);
        popup.cancel();
        popupRepo.flush();

        // 시작 전 회차 및 해당 회차 예약 일괄 취소
        timeSlotService.cancelUpcomingSlotsByPopup(popId);

        // 미입장 웨이팅(WAITING, CALLED) 일괄 취소
        int cancelledWaitings = waitingQueueRepo.bulkCancelActiveWaitingsByPopup(
                popId,
                List.of(WaitingStatus.WAITING, WaitingStatus.CALLED),
                WaitingStatus.CANCELLED
        );
        //log.info("[팝업 종료 연동] popId={} 종료로 인해 미입장 대기열 {}건이 CANCELLED 처리되었습니다.", popId, cancelledWaitings);

        return PopupStoreResponse.toDto(popup);
    }

    // ------------------------- 팝업 중지/재개/종료 메소드 끝------------------------

    // 팝업 상세 조회
    public PopupStoreResponse detailViewPopup(Long popId, String username) {
        PopupStore entity = popupRepo.findById(popId)
                .orElseThrow(() -> new PopupException(PopupErrorCode.POPUP_NOT_FOUND));

        long favCount = favoriteRepo.countPopupFavorites(popId);
        boolean isFav = resolveLoginMemberId(username)
                .map(memberId -> favoriteRepo.isPopupFavorited(memberId, popId))
                .orElse(false);

        return PopupStoreResponse.toDto(entity, isFav, favCount);
    }

    // 매니저 목록 (ADMIN 전용) : 배정 드롭다운용
    public List<ManagerResponse> listManagers(String username) {
        return accessService.listManagers(username);
    }

    // 담당 매니저 배정 (ADMIN 전용) : managerEmail을 비우면 배정 해제 / 바뀐 팝업 정보 반환
    @Transactional
    public PopupStoreResponse assignManager(Long popId, PopupManagerRequest request, String username) {
        accessService.checkAdmin(username); // ADMIN이 아니면 403
        PopupStore popup = popupRepo.findById(popId)
                .orElseThrow(() -> new PopupException(PopupErrorCode.POPUP_NOT_FOUND));
        String email = request.getManagerEmail();
        if (email == null || email.isBlank()) {
            popup.assignManager(null); // 배정 해제
        } else {
            popup.assignManager(accessService.getManagerByEmail(email)); // 없으면 404, MANAGER가 아니면 400
        }
        return PopupStoreResponse.toDto(popup);
    }

    // entity 목록 -> response 목록 변환
    private List<PopupStoreResponse> toDtoList(List<PopupStore> entityList) {
        List<PopupStoreResponse> dtoList = new ArrayList<PopupStoreResponse>();
        for (PopupStore e : entityList) {
            dtoList.add(PopupStoreResponse.toDto(e));
        }
        return dtoList;
    }

    // 필수값 체크 : 회차 생성에서 운영기간과 예약 시간을 사용하므로 반드시 필요
    private void checkRequired(PopupStoreRequest request) {
        if (request.getTitle() == null || request.getTitle().isBlank()
                || request.getLocation() == null || request.getLocation().isBlank()
                || request.getStartDate() == null || request.getEndDate() == null
                || request.getReservStart() == null || request.getReservEnd() == null) {
            throw new PopupException(PopupErrorCode.POPUP_REQUIRED_FIELD);
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

    // 비로그인일 경우 Optional.empty() 반환
    private Optional<Long> resolveLoginMemberId(String username) {
        if (username == null || username.isBlank() || "anonymousUser".equals(username)) {
            return Optional.empty();
        }
        return userRepo.findByUsername(username).map(User::getId);
    }
}
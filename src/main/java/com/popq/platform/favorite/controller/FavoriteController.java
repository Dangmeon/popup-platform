package com.popq.platform.favorite.controller;

import com.popq.platform.favorite.dto.FavoriteResponse;
import com.popq.platform.favorite.service.FavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class FavoriteController {

    FavoriteService favoriteService;

    @Autowired
    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    // 팝업스토어 찜 등록 : 201 Created
    @PostMapping("/api/popups/{popId}/favorites")
    public ResponseEntity<FavoriteResponse> addPopupFavorite(@PathVariable("popId") Long popId,
                                                             @AuthenticationPrincipal String username) {
        FavoriteResponse response = favoriteService.addPopupFavorite(popId, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 팝업스토어 찜 취소 : 200 OK
    @DeleteMapping("/api/popups/{popId}/favorites")
    public ResponseEntity<FavoriteResponse> removePopupFavorite(@PathVariable("popId") Long popId,
                                                                @AuthenticationPrincipal String username) {
        FavoriteResponse response = favoriteService.removePopupFavorite(popId, username);
        return ResponseEntity.ok(response);
    }

    /// 확인된 테스트 결과
    /// TC-01 : 비로그인 계정으로 POST http://localhost:8080/api/popups/1/favorites 호츌 -> 403 forbidden
    /// TC-02 : 로그인 계정으로 POST http://localhost:8080/api/popups/1/favorites 호츌 -> isFavorite : true
    /// TC-03 : 같은 계정으로 같은 팝업에 한번 더 POST 날리기 -> favoriteId : 1로 동일
    /// TC-04 : 조회 시 찜 기록과 찜 카운트가 넘어오는지 GET http://localhost:8080/api/popups/1
    /// TC-05 : 인기순 조회 시 찜 목록 많은 게 먼저 뜨게 하는 API 잘 동작하는지 GET http://localhost:8080/api/popups?sort=POPULAR
    /// TC-06 : 찜 취소 시 isFavorite이 false로 잘 바뀌는지 DELETE http://localhost:8080/api/popups/1/favorites
    /// TC-07 : 찜 취소 후 인기순 조회 시 찜 기록, 카운트가 잘 취소되고 까이는지
}
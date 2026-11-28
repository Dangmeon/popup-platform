package com.popq.platform.mypage.service;

import com.popq.platform.favorite.repository.FavoriteRepository;
import com.popq.platform.mypage.dto.MyFavoriteResponse;
import com.popq.platform.mypage.dto.MyReservationResponse;
import com.popq.platform.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyPageService {

    private final ReservationRepository reservationRepository;
    private final FavoriteRepository favoriteRepository;

    // 내 예약 목록 조회
    public List<MyReservationResponse> getMyReservations(String email) {
        return reservationRepository.findAllByEmailWithDetails(email)
                .stream()
                .map(MyReservationResponse::from)
                .toList();
    }

    // 내 찜/관심 목록 조회
    public List<MyFavoriteResponse> getMyFavorites(String email) {
        return favoriteRepository.findAllByEmailWithPopup(email)
                .stream()
                .map(MyFavoriteResponse::from)
                .toList();
    }
}
package com.popq.platform.service;

import com.popq.platform.dto.ReservationCreateRequest;
import com.popq.platform.dto.ReservationResponse;
import com.popq.platform.dto.ReservationUpdateRequest;
import com.popq.platform.entity.ReservationStatus;
import com.popq.platform.entity.TimeSlot;
import com.popq.platform.entity.User;
import com.popq.platform.exception.GlobalExceptionHandler;
import com.popq.platform.exception.ReservationErrorCode;
import com.popq.platform.exception.ReservationException;
import com.popq.platform.repository.ReservationRepository;
import com.popq.platform.repository.TimeSlotRepository;
import com.popq.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;

    // 예약 생성
    public ReservationResponse createReservation(Long memberId, ReservationCreateRequest request){

        // 1. 예약 인원 유효성 확인 (peopleCount >= 1)
        if(request.peopleCount() < 1){
            throw new ReservationException(ReservationErrorCode.INVALID_PEOPLE_COUNT);
        };

        // 2. 로그인 사용자 조회
        User user = userRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원이 아닙니다."));

        // 3. TimeSlot 조회 + 동시성 제어
        //    - 비관적 락 사용 시 여기서 lock
        TimeSlot timeSlot = timeSlotRepository.findById(request.timeSlotId())
                .orElseThrow(() -> new IllegalArgumentException("회차가 존재하지 않습니다."));

        // TimeSlot.increaseReservedCount(request.peopleCount());

        // 4. 예약 가능한 날짜/시간인지 확인
//        LocalDateTime now = LocalDateTime.now();
//
//        LocalTime openAt = LocalTime.parse(timeSlot.getStartTime());
//        LocalTime closeAt = LocalTime.parse(timeSlot.getEndTime());
//
//        if(now.isBefore(openAt) || now.isAfter(closeAt)){
//            throw new ReservationException(
//                    ReservationErrorCode.RESERVATION_NOT_FOUND
//            );
//        }


        // 5. 같은 회원 + 같은 슬롯 활성 예약 중복 확인
        //    - HOLD / CONFIRMED / USED → 재예약 X
        //    - CANCELLED / EXPIRED → 재예약 O
        List<ReservationStatus> activeStatuses = List.of(
                ReservationStatus.HOLD,
                ReservationStatus.CONFIRMED,
                ReservationStatus.USED
        );

        boolean exist = reservationRepository.existsActiveReservation(
                memberId,
                timeSlot.getTimeSlotId(),
                activeStatuses
        );

        if(exist){
            throw new ReservationException(ReservationErrorCode.DUPLICATE_RESERVATION);
        }

        // 6. 한 예약당 최대 인원 확인
        if(request.peopleCount() > 3){ // 내일 상수로 빼기
            throw new ReservationException(ReservationErrorCode.INVALID_PEOPLE_COUNT);
        }

        // 7. 최신 reservedCount 기준 잔여 정원 확인
        //    - reservedCount + peopleCount <= capacity
        if(timeSlot.getReservedCount() + request.peopleCount() > timeSlot.getCapacity()){
            throw new ReservationException(ReservationErrorCode.CAPACITY_EXCEEDED);
        }

        // 8. reservedCount 증가

        // 9. HOLD 예약 생성

        // 10. expiresAt 설정

        // 11. 예약 저장

        // 12. ReservationResponse 반환

        return null;
    }

    // 예약 상세 조회
    public ReservationResponse getReservation(Long memberId, Long reservationId){
        return null;
        // 1. reservationId로 예약 조회
        // 2. 예약 존재 여부 확인
        // 3. 본인 예약인지 확인
        // 4. time_slot / popup_store 정보 조회
        // 5. 상세정보 반환
    }

    // 예약 확정
    public ReservationResponse confirmReservation(Long memberId, Long reservationId){
        return null;
        // 1. reservationId로 예약 조회
        // 2. 본인 예약 여부 확인
        // 3. 예약 상태가 HOLD인지 확인
        // 4. expiresAt 만료 여부 확인
        // 5. 만료됐으면 EXPIRED 처리 + 정원 복구
        // 6. 만료 전이면 CONFIRMED로 변경
        // 7. QR 생성용 qr_token 발급
        // 8. 결과 반환
    }

    // 예약 변경
    public ReservationResponse updateReservation(Long memberId, Long reservationId, ReservationUpdateRequest request){
        return null;
        // 1. 예약 존재 확인
        // 2. 본인 예약인지 확인
        // 3. 변경 가능한 상태인지 확인
        // 4. 기존 time_slot 확인
        // 5. 새 time_slot 확인
        // 6. 변경 가능한 날짜/시간인지 확인
        // 7. peopleCount >= 1 확인
        // 8. peopleCount <= 팝업의 reserv_people_cap 확인
        // 9. 새 회차 정원 확인
        // 10. 동시성 제어
        // 11. 같은 슬롯이면 인원 차이만큼 reserved_count 조정
        // 12. 다른 슬롯이면 기존 슬롯 감소 + 새 슬롯 증가
        // 13. reservation의 time_slot / people_count 변경
        // 14. 결과 반환
    }

    // 예약 취소
    public ReservationResponse cancelReservation(Long memberId, Long reservationId){
        return null;
        // 1. reservationId로 예약 조회
        // 2. 본인 예약인지 확인
        // 3. 현재 상태 확인
        // 4. 취소 가능한 상태인지 확인
        // 5. 취소 가능 시간 정책 확인
        // 6. CANCELLED로 변경
        // 7. reserved_count에서 peopleCount만큼 복구
        // 8. 결과 반환
    }

    // QR 조회
    public byte[] getQr(Long memberId, Long reservationId){
        return null;
        // 1. reservationId로 예약 조회
        // 2. 본인 예약인지 확인
        // 3. 예약 상태가 CONFIRMED인지 확인
        // 4. qr_token 존재 여부 확인
        // 5. QR 이미지 생성
        // 6. byte[] 반환
    }
}

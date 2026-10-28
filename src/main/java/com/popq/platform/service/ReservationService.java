package com.popq.platform.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.popq.platform.dto.ReservationCreateRequest;
import com.popq.platform.dto.ReservationDetailResponse;
import com.popq.platform.dto.ReservationResponse;
import com.popq.platform.dto.ReservationUpdateRequest;
import com.popq.platform.entity.Reservation;
import com.popq.platform.entity.ReservationStatus;
import com.popq.platform.entity.TimeSlot;
import com.popq.platform.entity.User;
import com.popq.platform.exception.ReservationErrorCode;
import com.popq.platform.exception.ReservationException;
import com.popq.platform.repository.ReservationRepository;
import com.popq.platform.repository.TimeSlotRepository;
import com.popq.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;

    private static final int MAX_PEOPLE_COUNT = 3;
    private static final int HOLD_EXPIRE_MINUTES = 10;
    private static final int CANCEL_DEADLINE_MINUTES = 60;
    private static final List<ReservationStatus> ACTIVE_STATUSES =
            List.of(
                    ReservationStatus.HOLD,
                    ReservationStatus.CONFIRMED,
                    ReservationStatus.USED
            );


    // 예약 생성
    @Transactional
    public ReservationResponse createReservation(Long memberId, ReservationCreateRequest request) {

        // 1. 예약 인원 유효성 확인 (peopleCount >= 1)
        // 6. 한 예약당 최대 인원 확인
        if (request.peopleCount() < 1 ||
                request.peopleCount() > MAX_PEOPLE_COUNT) {
            throw new ReservationException(ReservationErrorCode.INVALID_PEOPLE_COUNT);
        }

        // 2. 로그인 사용자 조회
        // TODO: 회원 예외 처리 구조 통일 후 커스텀 예외로 변경
        User user = userRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원이 아닙니다."));

        // 3. TimeSlot 조회 + 동시성 제어
        //    - 비관적 락 사용 시 여기서 lock
        TimeSlot timeSlot = timeSlotRepository.findByIdForUpdate(request.timeSlotId())
                .orElseThrow(() -> new ReservationException(
                        ReservationErrorCode.TIMESLOT_NOT_FOUND
                ));

        // 4. 예약 가능한 날짜/시간인지 확인
        LocalDateTime openAt = LocalDateTime.of(
                timeSlot.getSlotDate().minusDays(TimeSlot.RESERV_OPEN_DAYS_BEFORE),
                TimeSlot.RESERV_OPEN_TIME
        );

        LocalDateTime closeAt = timeSlot.getSlotDate().atStartOfDay();

        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(openAt) || !now.isBefore(closeAt)) {
            throw new ReservationException(
                    ReservationErrorCode.RESERVATION_NOT_AVAILABLE
            );
        }

        // 5. 같은 회원 + 같은 슬롯 활성 예약 중복 확인
        //    - HOLD / CONFIRMED / USED → 재예약 X
        //    - CANCELLED / EXPIRED → 재예약 O
        boolean exist = reservationRepository.existsActiveReservation(
                memberId,
                timeSlot.getTimeSlotId(),
                ACTIVE_STATUSES
        );

        if (exist) {
            throw new ReservationException(ReservationErrorCode.DUPLICATE_RESERVATION);
        }

        // 7. 최신 reservedCount 기준 잔여 정원 확인
        //    - reservedCount + peopleCount <= capacity
        if (timeSlot.getReservedCount() + request.peopleCount() > timeSlot.getCapacity()) {
            throw new ReservationException(ReservationErrorCode.CAPACITY_EXCEEDED);
        }

        // 8. reservedCount 증가
        timeSlot.increaseReservedCount(request.peopleCount());

        // 9. HOLD 예약 생성
        // 10. expiresAt 설정
        LocalDateTime expiresAt = now.plusMinutes(HOLD_EXPIRE_MINUTES);
        Reservation reservation = Reservation.create(
                user,
                timeSlot,
                request.peopleCount(),
                now,
                expiresAt
        );

        // 11. 예약 저장
        Reservation savedReservation = reservationRepository.save(reservation);

        // 12. ReservationResponse 반환
        return ReservationResponse.fromReservation(savedReservation);
    }

    // 예약 상세 조회
    @Transactional(readOnly = true)
    public ReservationDetailResponse getReservation(Long memberId, Long reservationId){
        // 1. reservationId로 예약 조회
        // 2. 예약 존재 여부 확인
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND));

        // 3. 본인 예약인지 확인
        // TODO: 회원 예외 처리 구조 통일 후 커스텀 예외로 변경
        if(!reservation.getUser().getId().equals(memberId)){
            throw new IllegalArgumentException("회원이 아닙니다.");
        }

        // 4. time_slot / popup_store 정보 조회
        // 5. 상세정보 반환
        return ReservationDetailResponse.fromDetailReservation(reservation);
    }

    // 예약 확정
    @Transactional
    public ReservationResponse confirmReservation(Long memberId, Long reservationId){
        return null;
    }

    // 예약 취소
    @Transactional
    public ReservationResponse cancelReservation(Long memberId, Long reservationId){
        return null;
    }

    // QR 조회
    @Transactional(readOnly = true)
    public byte[] getQr(Long memberId, Long reservationId){
        return null;
    }
}

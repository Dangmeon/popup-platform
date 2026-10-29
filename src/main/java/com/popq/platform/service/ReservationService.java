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
        // 1. reservationId로 예약 조회
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND));

        // 2. 본인 예약 여부 확인
        // TODO: 회원 예외 처리 구조 통일 후 커스텀 예외로 변경
        if(!reservation.getUser().getId().equals(memberId)){
            throw new IllegalArgumentException("회원이 아닙니다.");
        }

        // 3. 예약 상태가 HOLD인지 확인
        if(reservation.getReservationStatus() != ReservationStatus.HOLD){
            throw new ReservationException(ReservationErrorCode.INVALID_RESERVATION_STATUS);
        }

        // 4. expiresAt 만료 여부 확인
        // 5. 만료됐으면 EXPIRED 처리 + 정원 복구
        // 6. 만료 전이면 CONFIRMED로 변경
        /**
         *  나중에 레디스 구현 시 별도 서비스에서 처리 후 호출하게
         * **/
        TimeSlot timeSlot = timeSlotRepository.findByIdForUpdate(reservation.getTimeSlot().getTimeSlotId())
                .orElseThrow(() -> new ReservationException(ReservationErrorCode.TIMESLOT_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();

        if(!now.isBefore(reservation.getExpiresAt())){
            reservation.expire();
            timeSlot.decreaseReservedCount(reservation.getPeopleCount());

            return ReservationResponse.fromReservation(reservation);
        }

        // 7. QR 생성용 qr_token 발급
        // TODO: 웨이팅/입장 검증 API 확정 후
        // qrToken 대신 관리자 검증 페이지 URL을 QR에 인코딩하도록 변경
        String token = UUID.randomUUID().toString();

        reservation.confirm(token);

        // 8. 결과 반환
        return ReservationResponse.fromReservation(reservation);
    }

    // 예약 변경
    @Transactional
    public ReservationResponse updateReservation(Long memberId, Long reservationId, ReservationUpdateRequest request){
        // 1. 예약 존재 확인
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND));

        // 2. 본인 예약인지 확인
        // TODO: 회원 예외 처리 구조 통일 후 커스텀 예외로 변경
        if(!reservation.getUser().getId().equals(memberId)){
            throw new IllegalArgumentException("회원이 아닙니다.");
        }

        // 3. 변경 가능한 상태인지 확인
        // CONFIRMED 상태의 예약만 변경 가능
        if (reservation.getReservationStatus() != ReservationStatus.CONFIRMED) {
            throw new ReservationException(
                    ReservationErrorCode.INVALID_RESERVATION_STATUS
            );
        }

        // 4. 기존/변경 회차 ID 확인
        // 5. 같은 회차는 단일 락 획득
        //    다른 회차는 데드락 방지를 위해 작은 ID부터 순서대로 비관적 락 획득
        Long oldTimeSlotId = reservation.getTimeSlot().getTimeSlotId();
        Long newTimeSlotId = request.timeSlotId();

        TimeSlot oldTimeSlot;
        TimeSlot newTimeSlot;

        if (oldTimeSlotId.equals(newTimeSlotId)) {

            // 같은 회차 → 하나만 락
            TimeSlot timeSlot = timeSlotRepository.findByIdForUpdate(oldTimeSlotId)
                    .orElseThrow(() -> new ReservationException(ReservationErrorCode.TIMESLOT_NOT_FOUND));

            oldTimeSlot = timeSlot;
            newTimeSlot = timeSlot;

        } else {

            // 다른 회차 → 항상 작은 ID부터 락
            Long firstId = Math.min(oldTimeSlotId, newTimeSlotId);
            Long secondId = Math.max(oldTimeSlotId, newTimeSlotId);

            TimeSlot firstTimeSlot = timeSlotRepository.findByIdForUpdate(firstId)
                    .orElseThrow(() -> new ReservationException(ReservationErrorCode.TIMESLOT_NOT_FOUND));

            TimeSlot secondTimeSlot = timeSlotRepository.findByIdForUpdate(secondId)
                    .orElseThrow(() -> new ReservationException(ReservationErrorCode.TIMESLOT_NOT_FOUND));

            oldTimeSlot = oldTimeSlotId.equals(firstId) ? firstTimeSlot : secondTimeSlot;

            newTimeSlot = newTimeSlotId.equals(firstId) ? firstTimeSlot : secondTimeSlot;
        }

        // 6. 변경할 회차의 예약 가능 기간 확인
        LocalDateTime openAt = LocalDateTime.of(
                newTimeSlot.getSlotDate().minusDays(TimeSlot.RESERV_OPEN_DAYS_BEFORE),
                TimeSlot.RESERV_OPEN_TIME
        );

        LocalDateTime closeAt = newTimeSlot.getSlotDate().atStartOfDay();

        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(openAt) || !now.isBefore(closeAt)) {
            throw new ReservationException(
                    ReservationErrorCode.RESERVATION_NOT_AVAILABLE
            );
        }

        // 7. 변경할 예약 인원 유효성 확인
        // 예약 가능 인원: 1 ~ MAX_PEOPLE_COUNT
        if (request.peopleCount() < 1 ||
                request.peopleCount() > MAX_PEOPLE_COUNT) {
            throw new ReservationException(ReservationErrorCode.INVALID_PEOPLE_COUNT);
        }

        // 8. 기존 회차와 변경할 회차가 동일한지 확인
        boolean sameTimeSlot = oldTimeSlot.getTimeSlotId().equals(newTimeSlot.getTimeSlotId());

        if(sameTimeSlot){
            // 9. 같은 회차인 경우 기존 인원과 변경 인원의 차이 계산
            int difference = request.peopleCount() - reservation.getPeopleCount();

            // 10. 인원이 증가하는 경우 추가 인원만큼 정원 확인 후 증가
            if(difference > 0){
                if(oldTimeSlot.getReservedCount() + difference > oldTimeSlot.getCapacity()){
                    throw new ReservationException(ReservationErrorCode.CAPACITY_EXCEEDED);
                }

                oldTimeSlot.increaseReservedCount(difference);

                // 11. 인원이 감소하는 경우 감소한 인원만큼 정원 복구
            }else if (difference < 0) {
                oldTimeSlot.decreaseReservedCount(Math.abs(difference));
            }

            // 12. 예약 인원 변경
            reservation.updateTimeSlot(newTimeSlot, request.peopleCount());
        }else {

            // 13. 다른 회차로 변경하는 경우 새 회차 정원 확인
            if(newTimeSlot.getReservedCount() + request.peopleCount() > newTimeSlot.getCapacity()){
                throw new ReservationException(ReservationErrorCode.CAPACITY_EXCEEDED);
            }

            // 14. 기존 회차에서 기존 예약 인원만큼 정원 복구
            oldTimeSlot.decreaseReservedCount(reservation.getPeopleCount());

            // 15. 새 회차에 변경할 예약 인원만큼 정원 증가
            newTimeSlot.increaseReservedCount(request.peopleCount());

            // 16. 예약의 회차 및 인원 변경
            reservation.updateTimeSlot(newTimeSlot, request.peopleCount());

        }

        String newQrToken = UUID.randomUUID().toString();
        reservation.updateQrToken(newQrToken);

        // 17. 변경 결과 반환
        return ReservationResponse.fromReservation(reservation);
    }

    // 예약 취소
    @Transactional
    public ReservationResponse cancelReservation(Long memberId, Long reservationId){
        // 1. reservationId로 예약 조회
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND));
        // 2. 본인 예약인지 확인
        if(!reservation.getUser().getId().equals(memberId)){
            throw new IllegalArgumentException("회원이 아닙니다.");
        }
        // 3. 현재 상태 확인
        // 4. 취소 가능한 상태인지 확인
        // 5. 취소 가능 시간 정책 확인
        if(reservation.getReservationStatus() != ReservationStatus.CONFIRMED){
            throw new ReservationException(ReservationErrorCode.INVALID_RESERVATION_STATUS);
        }

        TimeSlot timeSlot = timeSlotRepository.findByIdForUpdate(
                        reservation.getTimeSlot().getTimeSlotId())
                .orElseThrow(() -> new ReservationException(ReservationErrorCode.TIMESLOT_NOT_FOUND));

        LocalDateTime visitStart = LocalDateTime.of(
                timeSlot.getSlotDate(),
                LocalTime.parse(timeSlot.getStartTime())
        );

        LocalDateTime cancelDeadLine = visitStart.minusMinutes(CANCEL_DEADLINE_MINUTES);
        LocalDateTime now = LocalDateTime.now();

        if (!now.isBefore(cancelDeadLine)) {
            throw new ReservationException(
                    ReservationErrorCode.CANCELLATION_DEADLINE_PASSED
            );
        }

        // 7. reserved_count에서 peopleCount만큼 복구
        timeSlot.decreaseReservedCount(reservation.getPeopleCount());

        // 6. CANCELLED로 변경
        reservation.cancel();

        // 8. 결과 반환
        return ReservationResponse.fromReservation(reservation);
    }

    // QR 조회
    @Transactional(readOnly = true)
    public byte[] getQr(Long memberId, Long reservationId){
        // 1. reservationId로 예약 조회
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND));
        // 2. 본인 예약인지 확인
        if(!reservation.getUser().getId().equals(memberId)){
            throw new IllegalArgumentException("회원이 아닙니다.");
        }
        // 3. 예약 상태가 CONFIRMED인지 확인
        if(reservation.getReservationStatus() != ReservationStatus.CONFIRMED){
            throw new ReservationException(ReservationErrorCode.INVALID_RESERVATION_STATUS);
        }

        String qrToken = reservation.getQrToken();
        // 4. qr_token 존재 여부 확인
        if(qrToken == null){
            throw new ReservationException(ReservationErrorCode.QR_TOKEN_NOT_FOUND);
        }

        // 5. QR 이미지 생성
        MultiFormatWriter writer = new MultiFormatWriter();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try{
            BitMatrix bitMatrix = writer.encode(
                    qrToken,
                    BarcodeFormat.QR_CODE,
                    300,
                    300
            );

            MatrixToImageWriter.writeToStream(
                    bitMatrix,
                    "PNG",
                    outputStream
            );

            // 6. byte[] 반환
            return outputStream.toByteArray();
        } catch (WriterException | IOException e) {
            throw new ReservationException(
                    ReservationErrorCode.QR_GENERATION_FAILED
            );
        }
    }
}

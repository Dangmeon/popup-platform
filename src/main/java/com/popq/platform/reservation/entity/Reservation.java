package com.popq.platform.reservation.entity;

import com.popq.platform.timeslot.entity.TimeSlot;
import com.popq.platform.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "reservation")
public class Reservation {

    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "reservation_seq_gen"
    )
    @SequenceGenerator(
            name = "reservation_seq_gen",
            sequenceName = "reservation_seq",
            allocationSize = 1
    )
    @Column(name = "reservation_id")
    private Long reservationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_slot_id", nullable = false)
    private TimeSlot timeSlot;

    @Column(name = "people_count", nullable = false)
    private int peopleCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "reservation_status", nullable = false)
    private ReservationStatus reservationStatus;

    @Column(name = "reserved_at", nullable = false)
    private LocalDateTime reservedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "qr_token", unique = true)
    private String qrToken;

    public void confirm(String qrToken){
        this.reservationStatus = ReservationStatus.CONFIRMED;
        this.qrToken = qrToken;
    }

    public void cancel(){
        this.reservationStatus = ReservationStatus.CANCELLED;
    }

    public void expire(){
        this.reservationStatus = ReservationStatus.EXPIRED;
    }

    public void changeReservation(TimeSlot timeSlot, int peopleCount){
        this.timeSlot = timeSlot;
        this.peopleCount = peopleCount;
    }

    public void renewQrToken(String qrToken) {
        this.qrToken = qrToken;
    }

    @Builder(access = AccessLevel.PRIVATE)
    private Reservation(User user, TimeSlot timeSlot, int peopleCount, LocalDateTime reservedAt, LocalDateTime expiresAt
    ) {
        this.user = user;
        this.timeSlot = timeSlot;
        this.peopleCount = peopleCount;
        this.reservationStatus = ReservationStatus.HOLD;
        this.reservedAt = reservedAt;
        this.expiresAt = expiresAt;
    }

    public static Reservation create(User user, TimeSlot timeSlot, int peopleCount, LocalDateTime reservedAt, LocalDateTime expiresAt
    ) {
        return Reservation.builder()
                .user(user)
                .timeSlot(timeSlot)
                .peopleCount(peopleCount)
                .reservedAt(reservedAt)
                .expiresAt(expiresAt)
                .build();
    }
}

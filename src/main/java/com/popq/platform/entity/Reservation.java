package com.popq.platform.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
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

//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "member_id", nullable = false)
//    private Member member;

    // 임시
    @Column(name = "member_id", nullable = false)
    private Long memberId;

//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "time_slot_id", nullable = false)
//    private TimeSlot timeSlot;

    // 임시
    @Column(name = "time_slot_id", nullable = false)
    private Long timeSlotId;

    @Column(name = "people_count", nullable = false)
    private int peopleCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "reservation_status", nullable = false)
    private ReservationStatus reservationStatus;

    @Column(name = "reserved_at", nullable = false)
    private LocalDateTime reservedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "qr_token")
    private String qrToken;
}

package com.popq.platform.favorite.entity;

import com.popq.platform.entity.PopupStore;
import com.popq.platform.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity

// [중복 방지 제약조건]
// 같은 회원이 같은 팝업을 연속으로 2번 클릭해 요청이 들어오더라도
// DB 선에서 중복 행이 쌓이지 않도록 복합 유니크 제약조건 걸기

@Table(
        name = "favorite",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_favorite_member_popup",
                        columnNames = {"member_id", "pop_id", "target_type"}
                )
        }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Favorite {

    // Oracle DB는 MySQL의 AUTO_INCREMENT가 없으므로 시퀀스를 통해 PK의 번호를 자동 증가
    // allocationSize = 1 : JPA가 시퀀스 번호를 미리 50개씩 땡겨쓰지 않고 DB 시퀀스 값과 1:1 매칭
    @Id
    @SequenceGenerator(name = "favorite_seq_gen", sequenceName = "favorite_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "favorite_seq_gen")
    @Column(name = "favorite_id")
    private Long favoriteId;

    // 찜을 누른 회원 - 지연 로딩을 걸어 Favorite 테이블만 조회할 때 불필요하게 User 테이블까지 조인하여 조회하지 않음
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private User member;

    // 상품 찜 컬럼은 테이블에 존재하지만 이번 기능 범위에서 제외되었으므로 항상 null로 유지
    @Column(name = "prd_id")
    private Long prdId;

    // 찜 대상 팝업스토어
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pop_id")
    private PopupStore popupStore;

    // 찜 대상 구분값 (상품은 찜에서 제외되었으므로 POPUP 고정)
    @Column(name = "target_type", length = 10, nullable = false)
    @Builder.Default
    private String targetType = "POPUP";

    // 엔티티가 처음 INSERT 될 때 현재 시각을 자동으로 넣어준다 (수정 불가)
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // [정적 팩토리 메서드]
    // 외부에서 빌더로 일일이 필드값을 세팅하다가 나오는 휴먼 에러를 방지하기 위해,
    // "팝업 찜 생성" 메서드로 생성 규칙을 한 곳에 캡슐화
    public static Favorite createPopupFavorite(User member, PopupStore popupStore) {
        return Favorite.builder()
                .member(member)
                .popupStore(popupStore)
                .prdId(null)
                .targetType("POPUP")
                .build();
    }
}
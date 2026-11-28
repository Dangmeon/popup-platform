package com.popq.platform.coupon;

import com.popq.platform.auth.entity.Role;
import com.popq.platform.auth.entity.User;
import com.popq.platform.auth.repository.UserRepository;
import com.popq.platform.coupon.dto.CouponCreateRequest;
import com.popq.platform.coupon.dto.UserCouponResponse;
import com.popq.platform.coupon.entity.Coupon;
import com.popq.platform.coupon.entity.UserCoupon;
import com.popq.platform.coupon.repository.CouponRepository;
import com.popq.platform.coupon.repository.UserCouponRepository;
import com.popq.platform.coupon.service.CouponService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CouponService couponService;

    private User createUser(Long id, String username) {
        User user = User.builder()
                .username(username)
                .password("ENCODED_PW")
                .name("테스터")
                .phone("010-1234-5678")
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Coupon createCoupon(Long id, String name, int discountAmount, int stock, LocalDateTime expiredAt) {
        Coupon coupon = Coupon.builder()
                .name(name)
                .discountAmount(discountAmount)
                .stock(stock)
                .expiredAt(expiredAt)
                .build();
        ReflectionTestUtils.setField(coupon, "id", id);
        return coupon;
    }

    @Test
    @DisplayName("관리자 쿠폰 생성 시 쿠폰을 저장하고 생성된 ID를 반환한다")
    void createCoupon_success() {
        CouponCreateRequest request = new CouponCreateRequest();
        request.setName("오픈 기념 3000원 할인");
        request.setDiscountAmount(3000);
        request.setStock(100);
        request.setExpiredAt(LocalDateTime.now().plusDays(7));

        Coupon savedCoupon = createCoupon(10L, request.getName(), request.getDiscountAmount(), request.getStock(), request.getExpiredAt());
        given(couponRepository.save(any(Coupon.class))).willReturn(savedCoupon);

        Long createdId = couponService.createCoupon(request);

        assertThat(createdId).isEqualTo(10L);
        then(couponRepository).should().save(any(Coupon.class));
    }

    @Test
    @DisplayName("쿠폰 발급 성공 시 재고가 1 감소하고 UserCoupon이 저장된다")
    void issueCoupon_success() {
        User user = createUser(1L, "tester01@gmail.com");
        Coupon coupon = createCoupon(10L, "웰컴 쿠폰", 5000, 10, LocalDateTime.now().plusDays(5));

        given(userRepository.findByUsername("tester01@gmail.com")).willReturn(Optional.of(user));
        given(couponRepository.findByIdForUpdate(10L)).willReturn(Optional.of(coupon));
        given(userCouponRepository.existsByUserAndCoupon(user, coupon)).willReturn(false);
        given(userCouponRepository.save(any(UserCoupon.class))).willAnswer(invocation -> invocation.getArgument(0));

        couponService.issueCoupon("tester01@gmail.com", 10L);

        assertThat(coupon.getStock()).isEqualTo(9);
        then(userCouponRepository).should().save(any(UserCoupon.class));
    }

    @Test
    @DisplayName("이미 발급받은 쿠폰을 다시 발급 요청하면 예외가 발생한다")
    void issueCoupon_fail_alreadyIssued() {
        User user = createUser(1L, "tester01@gmail.com");
        Coupon coupon = createCoupon(10L, "웰컴 쿠폰", 5000, 10, LocalDateTime.now().plusDays(5));

        given(userRepository.findByUsername("tester01@gmail.com")).willReturn(Optional.of(user));
        given(couponRepository.findByIdForUpdate(10L)).willReturn(Optional.of(coupon));
        given(userCouponRepository.existsByUserAndCoupon(user, coupon)).willReturn(true);

        assertThatThrownBy(() -> couponService.issueCoupon("tester01@gmail.com", 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 발급받은 쿠폰입니다.");

        assertThat(coupon.getStock()).isEqualTo(10);
        then(userCouponRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("쿠폰 재고가 0일 때 발급 요청하면 예외가 발생한다")
    void issueCoupon_fail_outOfStock() {
        User user = createUser(1L, "tester01@gmail.com");
        Coupon coupon = createCoupon(10L, "선착순 쿠폰", 5000, 0, LocalDateTime.now().plusDays(5));

        given(userRepository.findByUsername("tester01@gmail.com")).willReturn(Optional.of(user));
        given(couponRepository.findByIdForUpdate(10L)).willReturn(Optional.of(coupon));
        given(userCouponRepository.existsByUserAndCoupon(user, coupon)).willReturn(false);

        assertThatThrownBy(() -> couponService.issueCoupon("tester01@gmail.com", 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("쿠폰이 모두 소진되었습니다.");

        then(userCouponRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("내 쿠폰 목록 조회 시 UserCouponResponse 리스트로 반환한다")
    void getMyCoupons_success() {
        User user = createUser(1L, "tester01@gmail.com");
        Coupon coupon = createCoupon(10L, "POPQ 쿠폰", 2000, 50, LocalDateTime.of(2026, 12, 31, 23, 59));
        UserCoupon userCoupon = UserCoupon.builder()
                .user(user)
                .coupon(coupon)
                .build();
        ReflectionTestUtils.setField(userCoupon, "id", 100L);

        given(userRepository.findByUsername("tester01@gmail.com")).willReturn(Optional.of(user));
        given(userCouponRepository.findAllByUserWithCoupon(user)).willReturn(List.of(userCoupon));

        List<UserCouponResponse> responses = couponService.getMyCoupons("tester01@gmail.com");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getUserCouponId()).isEqualTo(100L);
        assertThat(responses.get(0).getCouponId()).isEqualTo(10L);
        assertThat(responses.get(0).getName()).isEqualTo("POPQ 쿠폰");
        assertThat(responses.get(0).getDiscountAmount()).isEqualTo(2000);
        assertThat(responses.get(0).isUsed()).isFalse();
    }

    @Test
    @DisplayName("할인 금액 계산 시 총액보다 할인액이 크더라도 0원 미만으로 내려가지 않는다")
    void calculateDiscountedPrice_floorAtZero() {
        User user = createUser(1L, "tester01@gmail.com");
        Coupon coupon = createCoupon(10L, "10000원 쿠폰", 10000, 10, LocalDateTime.now().plusDays(3));
        UserCoupon userCoupon = UserCoupon.builder().user(user).coupon(coupon).build();
        ReflectionTestUtils.setField(userCoupon, "id", 200L);

        given(userCouponRepository.findById(200L)).willReturn(Optional.of(userCoupon));

        int discounted = couponService.calculateDiscountedPrice("tester01@gmail.com", 200L, 4000);

        assertThat(discounted).isZero();
    }

    @Test
    @DisplayName("다른 사용자 소유의 쿠폰 사용 시 예외가 발생한다")
    void useCoupon_fail_notOwner() {
        User owner = createUser(1L, "owner@gmail.com");
        Coupon coupon = createCoupon(10L, "할인 쿠폰", 3000, 10, LocalDateTime.now().plusDays(3));
        UserCoupon userCoupon = UserCoupon.builder().user(owner).coupon(coupon).build();
        ReflectionTestUtils.setField(userCoupon, "id", 200L);

        given(userCouponRepository.findById(200L)).willReturn(Optional.of(userCoupon));

        assertThatThrownBy(() -> couponService.useCoupon("other@gmail.com", 200L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("본인 소유의 쿠폰이 아닙니다.");

        assertThat(userCoupon.isUsed()).isFalse();
    }

    @Test
    @DisplayName("만료된 쿠폰 사용 시 예외가 발생한다")
    void useCoupon_fail_expired() {
        User user = createUser(1L, "tester01@gmail.com");
        Coupon expiredCoupon = createCoupon(10L, "만료 쿠폰", 3000, 10, LocalDateTime.now().minusMinutes(1));
        UserCoupon userCoupon = UserCoupon.builder().user(user).coupon(expiredCoupon).build();
        ReflectionTestUtils.setField(userCoupon, "id", 200L);

        given(userCouponRepository.findById(200L)).willReturn(Optional.of(userCoupon));

        assertThatThrownBy(() -> couponService.useCoupon("tester01@gmail.com", 200L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("만료된 쿠폰입니다.");

        assertThat(userCoupon.isUsed()).isFalse();
    }

    @Test
    @DisplayName("유효한 쿠폰 사용 시 isUsed가 true로 변경되고 중복 사용 시 예외가 발생한다")
    void useCoupon_success_and_failWhenAlreadyUsed() {
        User user = createUser(1L, "tester01@gmail.com");
        Coupon coupon = createCoupon(10L, "할인 쿠폰", 3000, 10, LocalDateTime.now().plusDays(3));
        UserCoupon userCoupon = UserCoupon.builder().user(user).coupon(coupon).build();
        ReflectionTestUtils.setField(userCoupon, "id", 200L);

        given(userCouponRepository.findById(200L)).willReturn(Optional.of(userCoupon));

        couponService.useCoupon("tester01@gmail.com", 200L);
        assertThat(userCoupon.isUsed()).isTrue();

        assertThatThrownBy(() -> couponService.useCoupon("tester01@gmail.com", 200L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 사용한 쿠폰입니다.");
    }
}
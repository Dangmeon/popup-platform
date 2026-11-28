package com.popq.platform.coupon;

import com.popq.platform.auth.config.JwtTokenProvider;
import com.popq.platform.auth.config.SecurityConfig;
import com.popq.platform.coupon.controller.AdminCouponController;
import com.popq.platform.coupon.controller.CouponController;
import com.popq.platform.coupon.dto.UserCouponResponse;
import com.popq.platform.coupon.service.CouponService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {CouponController.class, AdminCouponController.class})
@Import(SecurityConfig.class)
class CouponControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CouponService couponService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private void mockValidToken(String token, String username, String role) {
        given(jwtTokenProvider.validateToken(token)).willReturn(true);
        given(jwtTokenProvider.getUsername(token)).willReturn(username);
        given(jwtTokenProvider.getRole(token)).willReturn(role);
    }

    @Test
    @DisplayName("관리자 쿠폰 생성 요청 시 200 OK와 완료 메시지를 반환한다")
    void createCoupon_success() throws Exception {
        String token = "admin.jwt.token";
        mockValidToken(token, "admin@gmail.com", "ADMIN");

        String body = """
                {
                  "name": "신규 회원 쿠폰",
                  "discountAmount": 3000,
                  "stock": 50,
                  "expiredAt": "2026-12-31T23:59:00"
                }
                """;

        mockMvc.perform(post("/api/admin/coupons")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string("쿠폰이 성공적으로 생성되었습니다."));

        then(couponService).should().createCoupon(any());
    }

    @Test
    @DisplayName("MANAGER가 쿠폰 생성을 요청하면 403을 반환한다")
    void createCoupon_fail_manager() throws Exception {
        String token = "manager.jwt.token";
        mockValidToken(token, "manager@gmail.com", "MANAGER");

        String body = """
                {
                  "name": "신규 회원 쿠폰",
                  "discountAmount": 3000,
                  "stock": 50,
                  "expiredAt": "2026-12-31T23:59:00"
                }
                """;

        mockMvc.perform(post("/api/admin/coupons")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        then(couponService).should(never()).createCoupon(any());
    }

    @Test
    @DisplayName("사용자 쿠폰 발급 요청 시 200 OK와 완료 메시지를 반환한다")
    void issueCoupon_success() throws Exception {
        String token = "user.jwt.token";
        mockValidToken(token, "tester01@gmail.com", "USER");

        mockMvc.perform(post("/api/coupons/10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("쿠폰이 성공적으로 발급되었습니다."));

        then(couponService).should().issueCoupon("tester01@gmail.com", 10L);
    }

    @Test
    @DisplayName("내 쿠폰 목록 조회 요청 시 보유 쿠폰 목록 JSON을 반환한다")
    void getMyCoupons_success() throws Exception {
        String token = "user.jwt.token";
        mockValidToken(token, "tester01@gmail.com", "USER");

        UserCouponResponse item = UserCouponResponse.builder()
                .userCouponId(100L)
                .couponId(10L)
                .name("POPQ 쿠폰")
                .discountAmount(2000)
                .isUsed(false)
                .issuedAt(LocalDateTime.of(2026, 11, 26, 15, 0))
                .expiredAt(LocalDateTime.of(2026, 12, 31, 23, 59))
                .build();

        given(couponService.getMyCoupons("tester01@gmail.com")).willReturn(List.of(item));

        mockMvc.perform(get("/api/coupons/my")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userCouponId").value(100))
                .andExpect(jsonPath("$[0].couponId").value(10))
                .andExpect(jsonPath("$[0].name").value("POPQ 쿠폰"))
                .andExpect(jsonPath("$[0].discountAmount").value(2000))
                .andExpect(jsonPath("$[0].used").value(false));
    }
}
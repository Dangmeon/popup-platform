package com.popq.platform.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Request 헤더에서 토큰 추출
        String token = resolveToken(request);
        System.out.println(">>> 1. 추출된 토큰: " + token);

        try {
            // 2. 토큰 유효성 검사
            if (token != null && jwtTokenProvider.validateToken(token)) {
                System.out.println(">>> 2. 토큰 유효성 검사 결과: 성공");

                // 3. 토큰에서 이메일(username) 추출
                String username = jwtTokenProvider.getUsername(token);
                System.out.println(">>> 3. 토큰에서 추출한 username: " + username);

                // **3-1. 토큰에서 권한(role) 추출**
                String role = jwtTokenProvider.getRole(token);
                System.out.println(">>> 3-1. 토큰에서 추출한 role: " + role);

                // 4. 시큐리티 세션에 토큰에서 가져온 권한("ROLE_" + role)을 포함한 인증 정보 등록
                List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, authorities);

                SecurityContextHolder.getContext().setAuthentication(authentication);
                System.out.println(">>> 4. SecurityContext에 인증 및 권한 정보 등록 완료 (권한: ROLE_" + role + ")");
            } else if (token != null) {
                System.out.println(">>> 2. 토큰 유효성 검사 결과: 실패 (유효하지 않거나 만료된 토큰)");
            } else {
                System.out.println(">>> [알림] Authorization 헤더에 토큰이 없습니다.");
            }
        } catch (Exception e) {
            System.out.println(">>> [에러] 토큰 인증 처리 중 예외 발생: " + e.getMessage());
        }

        // 5. 다음 필터로 넘김
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
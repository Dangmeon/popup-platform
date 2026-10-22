package com.popq.platform.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    // WebSecurityCustomizer는 제거하거나 비워두는 것이 안전합니다.

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(securityContext -> securityContext
                        .securityContextRepository(new RequestAttributeSecurityContextRepository())
                )
                .authorizeHttpRequests(auth -> auth
                        // 1. 회원가입 및 로그인 등 인증 없이 접근해야 하는 경로 허용
                        .requestMatchers("/api/auth/**").permitAll()

                        // 2. 관리자만 접근 가능한 API 경로 설정
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // 3. 테스트용 API 등 인증이 필요한 경로 설정
                        .requestMatchers("/api/test").authenticated()

                        // 4. 그 외 나머지 요청은 인증된 사용자 모두 접근 가능
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
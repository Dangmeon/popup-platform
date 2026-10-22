package com.popq.platform.service;

import com.popq.platform.dto.SignupRequestDto;
import com.popq.platform.entity.Role;
import com.popq.platform.entity.User;
import com.popq.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void signup(SignupRequestDto requestDto) {
        String username = requestDto.getUsername();

        // 아이디 중복 확인
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 아이디입니다.");
        }

        // 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(requestDto.getPassword());

        // 사용자 엔티티 생성 및 저장 (이름, 전화번호, 권한 추가 세팅)
        User user = new User();
        user.setUsername(username);
        user.setPassword(encodedPassword);
        user.setName(requestDto.getName());
        user.setPhone(requestDto.getPhone());
        user.setRole(Role.USER); // 기본 회원가입 시 USER 권한 부여

        userRepository.save(user);
    }
}
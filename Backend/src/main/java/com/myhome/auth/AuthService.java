package com.myhome.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

@Service
public class AuthService {

    private final AuthMapper authMepper;

    // SecurityConfig에 등록한 비밀번호 해시 처리 도구다.
    private final PasswordEncoder passwordEncoder;


    public AuthService(AuthMapper authMapper, PasswordEncoder passwordEncoder) {
        this.authMepper = authMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 이메일 중복과 비밀번호 길이를 확인한 뒤,
     * 비밀번호를 해시 처리하여 사용자를 등록한다.
     */
    @Transactional
    public void signup(SignupRequest request) {

        // 이메일과 닉네임의 앞뒤 공백을 제거한다.
        String email = request.email().trim();
        String nickname = request.nickname().trim();

        // 이미 가입된 이메일이면 등록하지 않는다.
        if (authMepper.existsByEmail(email))  {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 사용 중인 이메일입니다."
            );
        }

        // BCrypt는 최대 72바이트까지 처리하므로 UTF-8 기준으로 검사한다.
        // 비밀번호는 공백도 입력값의 일부이므로 trim하지 않는다.
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "비밀번호는 UTF-8 기준 72바이트 이내로 입력해 주세요."
            );
        }

        // 원문 비밀번호 대신 해시 문자열을 저장한다.
        String passwordHash = passwordEncoder.encode(request.password());

        int insertRow = authMepper.insert(email, passwordHash, nickname);

        // 정상 등록은 1행이다. 다른 결과이면 예외를 발생시켜 롤백한다.
        if (insertRow != 1) {
            throw new IllegalStateException("회원가입 중 오류가 발생했습니다.");
        }
    }
}

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
     * 이메일 중복과 비밀번호 길이를 확인하고 회원 정보를 저장한다.
     * 비밀번호는 BCrypt 해시로, 미입력한 이름은 null로 저장한다.
     * 이 메서드는 회원가입만 처리하며 로그인 세션을 생성하지 않는다.
     */
    @Transactional
    public void signup(SignupRequest request) {

        // 이메일과 닉네임의 앞뒤 공백을 제거한다.
        String email = request.email().trim();
        String nickname = request.nickname().trim();

        // 이름은 선택 입력이며 닉네임과 별도로 저장한다.
        String name = request.name() == null || request.name().isBlank()
                ? null
                : request.name().trim();

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

        /**
         * 이름·나이·성별은 미입력 시 NULL로 저장한다.
         * 비밀번호는 해시만 저장하며, 가입 계정의 권한과 상태는 USER·ACTIVE로 지정한다.
         */
        int insertRow = authMepper.insert(
                email,
                passwordHash,
                name,
                nickname,
                request.age(),
                request.gender()
        );

        // 정상 등록은 1행이다. 다른 결과이면 예외를 발생시켜 롤백한다.
        if (insertRow != 1) {
            throw new IllegalStateException("회원가입 중 오류가 발생했습니다.");
        }
    }

    /**
     * 회원 정보 수정 화면에 필요한 본인의 정보를 조회한다.
     * 사용자 ID는 로그인 세션에서 확인한 값을 전달받는다.
     */
    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        ProfileResponse profile = authMepper.findProfile(userId);

        if (profile == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "회원 정보를 찾을 수 없습니다."
            );
        }

        return profile;
    }

    /**
     * 본인의 일반 회원 정보를 수정하고 저장된 닉네임을 반환한다.
     * 반환된 닉네임은 컨트롤러에서 세션 갱신에 사용한다.
     */
    @Transactional
    public String updateProfile(Long userId, ProfileUpdateRequest request) {
        requireActiveUser(userId);

        String name = request.name() == null || request.name().isBlank()
                ? null
                : request.name().trim();

        String nickname = request.nickname().trim();

        int updatedRows = authMepper.updateProfile(
                userId,
                name,
                nickname,
                request.age(),
                request.gender()
        );

        if (updatedRows != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "회원 상태가 변경되어 수정하지 못했습니다. 다시 로그인해 주세요."
            );
        }

        return nickname;
    }

    /**
     * 현재 비밀번호를 검증하고 새 비밀번호를 해시로 저장한다.
     * 트랜잭션 완료 후 컨트롤러에서 현재 로그인 세션을 종료한다.
     */
    @Transactional
    public void changePassword(Long userId, PasswordChangeRequest request) {
        AuthUser user = requireActiveUser(userId);

        String currentPassword = request.currentPassword();
        String newPassword = request.newPassword();

        // 비밀번호는 공백도 값의 일부이므로 trim하지 않는다.
        if (currentPassword.getBytes(StandardCharsets.UTF_8).length > 72
                || user.passwordHash() == null
                || user.passwordHash().isBlank()
                || !passwordEncoder.matches(currentPassword, user.passwordHash())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "현재 비밀번호가 일치하지 않습니다."
            );
        }

        if (newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "새 비밀번호는 UTF-8 기준 72바이트 이내로 입력해 주세요."
            );
        }

        if (passwordEncoder.matches(newPassword, user.passwordHash())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "현재 비밀번호와 다른 비밀번호를 입력해 주세요."
            );
        }

        String newPasswordHash = passwordEncoder.encode(newPassword);

        int updatedRows = authMepper.updatePassword(
                userId,
                user.passwordHash(),
                newPasswordHash
        );

        if (updatedRows != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "비밀번호 또는 회원 상태가 변경됐습니다. 다시 로그인해 주세요."
            );
        }
    }

    /**
     * DB의 현재 계정 상태를 확인한다.
     * 로그인 후 정지·탈퇴된 계정은 정보 변경을 허용하지 않는다.
     */
    private AuthUser requireActiveUser(Long userId) {
        AuthUser user = authMepper.findById(userId);

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "회원 정보를 찾을 수 없습니다."
            );
        }

        if (!"ACTIVE".equals(user.status())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "현재 계정 상태에서는 회원 정보를 변경할 수 없습니다."
            );
        }

        return user;
    }
}

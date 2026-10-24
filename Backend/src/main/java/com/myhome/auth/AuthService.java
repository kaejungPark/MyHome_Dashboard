package com.myhome.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import com.myhome.email.signup.SignupEmailService;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final AuthMapper authMepper;

    // SecurityConfig에 등록한 비밀번호 해시 처리 도구다.
    private final PasswordEncoder passwordEncoder;

    // 가입 전 이메일 인증 확인값을 검증하고 사용 완료 처리한다.
    private final SignupEmailService signupEmailService;

    public AuthService(AuthMapper authMapper, PasswordEncoder passwordEncoder, SignupEmailService signupEmailService) {
        this.authMepper = authMapper;
        this.passwordEncoder = passwordEncoder;
        this.signupEmailService = signupEmailService;
    }

    /**
     * 가입 전 이메일 인증을 검증하고 회원 정보를 저장한다.
     * 인증 확인값 사용 처리와 회원 저장은 같은 트랜잭션에서 실행한다.
     * 회원 저장이 실패하면 확인값 사용 처리도 함께 롤백된다.
     */
    @Transactional
    public void signup(SignupRequest request) {

        // 인증번호 발송·확인 단계와 동일한 방식으로 이메일을 정규화한다.
        String email = SignupEmailService.normalizeEmail(request.email());
        String nickname = request.nickname().trim();

        // 이름은 선택 입력이며 공백만 입력했다면 null로 저장한다.
        String name = request.name() == null || request.name().isBlank()
                ? null
                : request.name().trim();

        // 이미 가입된 이메일이면 인증 확인값을 사용하기 전에 거절한다.
        if (authMepper.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 사용 중인 이메일입니다."
            );
        }

        // 비밀번호의 공백도 실제 입력값이므로 trim하지 않는다.
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "비밀번호는 UTF-8 기준 72바이트 이내로 입력해 주세요."
            );
        }

        // 인증 이력 잠금을 잡기 전에 비밀번호 해시를 생성한다.
        String passwordHash = passwordEncoder.encode(request.password());

        // 이메일 일치·인증 완료·유효기간·미사용 여부를 검증한다.
        // 성공하면 확인값을 사용 처리하고 실제 인증 완료 시각을 반환한다.
        LocalDateTime emailVerifiedDt =
                signupEmailService.consumeForSignup(
                        email,
                        request.emailVerificationToken()
                );

        // true로 전달한 경우에만 동의로 저장한다.
        // false, null 또는 필드 미전달은 모두 미동의다.
        boolean emailNotificationEnabled =
                Boolean.TRUE.equals(request.emailNotificationEnabled());

        int insertedRows;

        try {
            insertedRows = authMepper.insert(
                    email,
                    passwordHash,
                    name,
                    nickname,
                    request.age(),
                    request.gender(),
                    emailVerifiedDt,
                    emailNotificationEnabled
            );
        } catch (DuplicateKeyException exception) {
            // 사전 확인 이후 발생한 중복도 DB 제약 조건으로 차단한다.
            // 예외가 전파되면 인증 확인값 사용 처리까지 롤백된다.
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 사용 중인 회원 정보입니다."
            );
        }

        if (insertedRows != 1) {
            throw new IllegalStateException(
                    "회원가입 중 오류가 발생했습니다."
            );
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
     * 활성 회원의 일반 정보와 이메일 알림 수신 동의를 수정한다.
     * 수신 동의 변경은 이메일 인증 상태를 변경하지 않는다.
     * 실제 알림 대상 여부는 알림 조회에서 인증 상태와 동의를 함께 검사한다.
     *
     * 반환된 닉네임은 컨트롤러에서 현재 로그인 세션을 갱신하는 데 사용한다.
     */
    @Transactional
    public String updateProfile(Long userId, ProfileUpdateRequest request) {
        requireActiveUser(userId);

        String name = request.name() == null || request.name().isBlank()
                ? null
                : request.name().trim();

        String nickname = request.nickname().trim();

        // 일반 회원 정보와 수신 동의 상태를 함께 저장한다.
        int updatedRows = authMepper.updateProfile(
                userId,
                name,
                nickname,
                request.age(),
                request.gender(),
                request.emailNotificationEnabled()
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
     * 일반 로그인 상태에서 현재 비밀번호를 검증하고
     * 새 비밀번호를 해시로 저장한다.
     *
     * 임시 로그인 상태의 변경은 PasswordResetService가 처리한다.
     * 매퍼에서 비밀번호 변경과 함께 인증 버전을 증가시키므로
     * 기존 로그인 세션은 이후 요청부터 유효하지 않게 된다.
     *
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

        // 검증했던 기존 해시가 여전히 일치할 때만 새 비밀번호를 저장한다.
        // 인증 버전도 함께 증가하여 다른 기존 로그인 세션을 무효화한다.
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

package com.myhome.auth.password;

import com.myhome.auth.AuthMapper;
import com.myhome.auth.AuthUser;
import com.myhome.auth.LoginUser;
import com.myhome.auth.PasswordChangeRequest;
import com.myhome.common.mail.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

/**
 * 비밀번호 찾기용 임시 비밀번호의 발급·검증·사용을 처리한다.
 *
 * 기존 일반 비밀번호는 발급 요청만으로 변경하지 않는다.
 * 임시 비밀번호로 로그인한 사용자가 새 비밀번호를 저장할 때
 * 실제 비밀번호를 변경한다.
 *
 * DB 트랜잭션과 메일 발송을 분리하기 위해 TransactionTemplate을 사용한다.
 */
@Service
public class PasswordResetService {

    private static final Logger log =
            LoggerFactory.getLogger(PasswordResetService.class);

    // 기존 회원 조회와 실제 비밀번호 변경에 사용한다.
    private final AuthMapper authMapper;

    // 임시 비밀번호 이력과 실패 횟수를 관리한다.
    private final PasswordResetMapper mapper;

    // 일반·임시 비밀번호의 BCrypt 해시 생성과 비교에 사용한다.
    private final PasswordEncoder encoder;

    // 기존 공통 텍스트 메일 발송 서비스를 재사용한다.
    private final MailService mailService;

    // DB 처리 범위를 직접 지정하고 메일 발송을 트랜잭션 밖으로 분리한다.
    private final TransactionTemplate transactions;

    // 임시 비밀번호 생성에는 보안용 난수 생성기를 사용한다.
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(
            AuthMapper authMapper,
            PasswordResetMapper mapper,
            PasswordEncoder encoder,
            MailService mailService,
            PlatformTransactionManager transactionManager
    ) {
        this.authMapper = authMapper;
        this.mapper = mapper;
        this.encoder = encoder;
        this.mailService = mailService;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * 가입한 이메일로 임시 비밀번호 발송을 요청한다.
     *
     * 처리 순서:
     * 1. 이메일 정규화 및 임시 비밀번호 생성
     * 2. 발급 대상·재발급 간격·시간당 횟수 검사
     * 3. 이전 요청 무효화 및 새 해시 저장
     * 4. DB 트랜잭션 종료 후 이메일 발송
     * 5. 발송 성공 시 활성화, 실패 시 FAILED 처리
     *
     * 계정 존재 여부와 발급 제한 여부는 외부 응답에서 구분하지 않는다.
     */
    public void send(PasswordResetRequest request) {
        // 가입 시 사용한 이메일 정규화 방식과 맞춘다.
        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        // 16바이트, 즉 128비트의 보안 난수를 생성한다.
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);

        // 복사해서 입력하기 편하도록 URL-safe Base64 문자열로 변환한다.
        // 패딩을 제외하면 22자이며 원문은 메일 발송에만 사용한다.
        String temporaryPassword =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(bytes);

        // DB에는 원문 대신 BCrypt 해시만 저장한다.
        // 해시 생성은 DB 잠금을 잡기 전에 수행한다.
        String passwordHash = encoder.encode(temporaryPassword);

        // 발송 완료·실패 처리가 다른 재발급 요청을 건드리지 않도록
        // 요청마다 고유 ID를 부여한다.
        String requestId = UUID.randomUUID().toString();

        /*
         * 발급 대상 검사와 이력 저장을 하나의 트랜잭션으로 실행한다.
         * 이 콜백이 끝나면 저장이 커밋되고 회원 행 잠금도 해제된다.
         *
         * null은 계정이 없거나 발급 제한에 해당한다는 내부 처리값이다.
         * 외부에는 원인을 구분하여 반환하지 않는다.
         */
        String recipient = transactions.execute(status -> {
            Long userId = mapper.findEligibleUserId(email);

            if (userId == null) {
                return null;
            }

            // 같은 회원의 동시 발급 요청을 순서대로 처리한다.
            AuthUser user = mapper.lockUser(userId);

            // 최초 조회 이후 회원 상태가 바뀌었을 수 있으므로 재확인한다.
            if (!active(user)) {
                return null;
            }

            LocalDateTime now = utcNow();
            LocalDateTime lastCreated = mapper.findLastCreated(userId);

            // 최근 발급 요청 후 1분이 지나지 않았다면 재발급하지 않는다.
            if (lastCreated != null
                    && lastCreated.plusMinutes(1).isAfter(now)) {
                return null;
            }

            // 발송 실패를 포함하여 최근 1시간에 최대 5회만 발급한다.
            if (mapper.countRecent(userId, now.minusHours(1)) >= 5) {
                return null;
            }

            // 새 요청만 사용할 수 있도록 이전 요청을 먼저 무효화한다.
            mapper.revokePrevious(userId);

            int inserted = mapper.insert(
                    requestId,
                    userId,
                    passwordHash,
                    user.authVersion(),
                    now.plusMinutes(15),
                    now
            );

            // 저장이 실패하면 트랜잭션을 롤백한다.
            // 이전 요청 무효화도 함께 롤백되며 메일은 보내지 않는다.
            if (inserted != 1) {
                throw new IllegalStateException("임시 비밀번호 저장 실패");
            }

            // 실제 수신 주소는 사용자 입력값이 아니라 DB 값을 사용한다.
            return user.email();
        });

        // 발급 대상이 아니거나 제한에 걸린 경우 별도 오류를 노출하지 않는다.
        if (recipient == null) {
            return;
        }

        try {
            /*
             * SMTP 통신은 DB 트랜잭션 밖에서 수행한다.
             * 메일 서버 응답을 기다리는 동안 DB 잠금이 유지되지 않는다.
             *
             * 일반 비밀번호와 임시 비밀번호는 서로 별도로 관리한다는 점을
             * 메일 본문에도 안내한다.
             */
            mailService.send(
                    recipient,
                    "[MyHome] 임시 비밀번호 안내",
                    """
                    안녕하세요. MyHome입니다.

                    임시 비밀번호: %s

                    로그인 화면에서 이메일과 위 임시 비밀번호를 입력해 주세요.
                    로그인 후 새 비밀번호로 변경해야 서비스를 이용할 수 있습니다.
                    임시 비밀번호는 발송 처리 완료 후 15분 동안 유효합니다.

                    기존 비밀번호는 새 비밀번호로 변경하기 전까지 유지됩니다.
                    본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                    """.formatted(temporaryPassword)
            );
        } catch (MailException exception) {
            // 발송 실패한 요청은 로그인에 사용할 수 없게 한다.
            mapper.markFailed(requestId);

            // 비밀번호 원문·해시·메일 본문은 로그에 남기지 않는다.
            log.warn(
                    "임시 비밀번호 메일 발송 실패: requestId={}",
                    requestId
            );

            // 계정 존재 여부를 응답 차이로 드러내지 않도록 정상 반환한다.
            return;
        }

        /*
         * SMTP 요청에 성공한 이력을 SENT 상태로 활성화한다.
         *
         * 발송 중 재발급 또는 비밀번호 변경이 있었다면 갱신 결과가 0이다.
         * 이 경우 이전 요청을 되살리지 않고 그대로 무효 상태로 둔다.
         */
        mapper.activate(requestId);
    }

    /**
     * 일반 비밀번호 인증 실패 후 임시 비밀번호를 검증한다.
     *
     * 정상 일반 비밀번호 로그인은 이 메서드를 호출하지 않는다.
     * 임시 비밀번호 검증 성공 시 변경 전용 LoginUser를 반환한다.
     *
     * 실패 횟수를 저장한 후 트랜잭션 밖에서 인증 예외를 던져야
     * 오입력 횟수가 롤백되지 않는다.
     */
    public LoginUser authenticateTemporary(
            String email,
            String password
    ) {
        LoginUser result = transactions.execute(status -> {
            AuthUser found = authMapper.findByEmail(email);

            if (found == null) {
                return null;
            }

            // 검증 실패 횟수가 동시에 덮어써지지 않도록 회원 행을 잠근다.
            AuthUser user = mapper.lockUser(found.id());

            if (!active(user)) {
                return null;
            }

            PasswordResetMapper.Ticket ticket =
                    mapper.findSent(user.id());

            // 계정 상태, 소유자, 인증 버전, 상태, 횟수, 만료를 검사한다.
            if (!valid(ticket, user)) {
                return null;
            }

            if (!encoder.matches(password, ticket.passwordHash())) {
                mapper.incrementFailure(ticket.requestId());

                // 여기서 예외를 던지지 않아야 실패 횟수가 커밋된다.
                return null;
            }

            // 일반 사용자·관리자 권한이 없는 임시 로그인 정보를 만든다.
            return LoginUser.forPasswordReset(user, ticket.requestId());
        });

        // execute가 끝난 이후이므로 실패 횟수는 이미 DB에 반영된 상태다.
        if (result == null) {
            throw new BadCredentialsException(
                    "이메일 또는 비밀번호를 확인해 주세요."
            );
        }

        return result;
    }

    /**
     * 임시 로그인 세션이 참조하는 발급 요청의 현재 유효성을 검사한다.
     *
     * 로그인 후에도 재발급·만료·사용 완료가 발생할 수 있으므로
     * 세션 정보만 믿지 않고 DB 이력을 다시 조회한다.
     *
     * 호출 전 필터에서 회원 인증 버전과 계정 상태를 먼저 확인한다.
     */
    public boolean isSessionValid(LoginUser loginUser, AuthUser user) {
        PasswordResetMapper.Ticket ticket =
                mapper.findByRequestId(
                        loginUser.getPasswordResetRequestId()
                );

        return valid(ticket, user);
    }

    /**
     * 임시 로그인 상태에서 실제 비밀번호를 새 비밀번호로 변경한다.
     *
     * 현재 비밀번호 입력란에는 이메일로 받은 임시 비밀번호를 입력한다.
     * 일반 로그인 상태의 비밀번호 변경은 기존 AuthService가 처리한다.
     *
     * 실제 비밀번호 저장·인증 버전 증가·임시 비밀번호 사용 처리는
     * 같은 트랜잭션에서 완료한다.
     *
     * 정상 반환 후 컨트롤러가 현재 세션을 종료한다.
     */
    public void changePassword(
            LoginUser loginUser,
            PasswordChangeRequest request
    ) {
        // 비밀번호의 공백도 값의 일부이므로 trim하지 않는다.
        String currentPassword = request.currentPassword();
        String newPassword = request.newPassword();

        // @Size는 문자 수 기준이다.
        // BCrypt 입력 제한에 맞춰 UTF-8 바이트 길이를 추가 검사한다.
        if (currentPassword.getBytes(StandardCharsets.UTF_8).length > 72
                || newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw badRequest(
                    "비밀번호는 UTF-8 기준 72바이트 이내로 입력해 주세요."
            );
        }

        // 계산 비용이 있는 해시 생성은 DB 잠금 전에 처리한다.
        String newPasswordHash = encoder.encode(newPassword);

        Boolean changed = transactions.execute(status -> {
            AuthUser user = mapper.lockUser(loginUser.getId());

            // 다른 요청이 먼저 비밀번호를 변경했거나 계정이 비활성화됐다면
            // 이전 임시 로그인 세션으로 변경할 수 없다.
            if (!active(user)
                    || user.authVersion() != loginUser.getAuthVersion()) {
                return false;
            }

            // 현재 세션이 인증받았던 정확한 발급 요청을 조회한다.
            PasswordResetMapper.Ticket ticket =
                    mapper.findByRequestId(
                            loginUser.getPasswordResetRequestId()
                    );

            if (!valid(ticket, user)) {
                return false;
            }

            // 변경 요청에서도 임시 비밀번호를 다시 확인한다.
            if (!encoder.matches(currentPassword, ticket.passwordHash())) {
                mapper.incrementFailure(ticket.requestId());
                return false;
            }

            // 기존 일반 비밀번호 또는 임시 비밀번호를 새 비밀번호로
            // 그대로 지정하지 못하도록 한다.
            if (encoder.matches(newPassword, ticket.passwordHash())
                    || encoder.matches(newPassword, user.passwordHash())) {
                throw badRequest(
                        "기존 비밀번호 및 임시 비밀번호와 다른 비밀번호를 입력해 주세요."
                );
            }

            // 비밀번호 저장과 동시에 AUTH_VERSION이 증가한다.
            int updated = authMapper.updatePassword(
                    user.id(),
                    user.passwordHash(),
                    newPasswordHash
            );

            // 둘 중 하나라도 실패하면 실제 비밀번호 변경까지 롤백한다.
            if (updated != 1 || mapper.markUsed(ticket.requestId()) != 1) {
                throw new IllegalStateException("비밀번호 변경 처리 실패");
            }

            return true;
        });

        // 검증 실패 횟수의 커밋 이후 안내 예외를 발생시킨다.
        if (!Boolean.TRUE.equals(changed)) {
            throw badRequest(
                    "임시 비밀번호가 일치하지 않거나 만료되었습니다. 다시 확인해 주세요."
            );
        }
    }

    /**
     * 임시 비밀번호 이력이 현재 회원에게 유효한지 공통 검사한다.
     *
     * 만료 시각과 현재 시각이 같아도 만료로 판단한다.
     * 실패 횟수가 5회에 도달하면 추가 사용을 허용하지 않는다.
     */
    private boolean valid(
            PasswordResetMapper.Ticket ticket,
            AuthUser user
    ) {
        return active(user)
                && ticket != null
                && ticket.userId().equals(user.id())
                && ticket.authVersion() == user.authVersion()
                && "SENT".equals(ticket.status())
                && ticket.failedAttempts() < 5
                && ticket.expiresAt().isAfter(utcNow());
    }

    /** 조회 결과가 존재하고 현재 활성 회원인지 확인한다. */
    private boolean active(AuthUser user) {
        return user != null && "ACTIVE".equals(user.status());
    }

    /** DB에 저장한 UTC 시각과 비교하기 위한 현재 UTC 시각이다. */
    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /** 사용자 입력 또는 만료 상태에 대한 400 응답 예외를 만든다. */
    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }
}
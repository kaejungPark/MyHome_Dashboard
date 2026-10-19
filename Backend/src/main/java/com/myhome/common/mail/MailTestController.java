package com.myhome.common.mail;

import com.myhome.common.response.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로컬 환경에서 이메일 발송 연결을 확인하는 임시 API다.
 * 수신 주소는 요청으로 받지 않고 실행 환경변수로 지정한다.
 */
@Profile("local")
@RestController
@RequestMapping("/api/mail")
public class MailTestController {
    private final MailService mailService;
    private final String testRecipient;

    public MailTestController(
            MailService mailService,
            @Value("${MAIL_TEST_RECIPIENT}") String testRecipient
    ) {
        this.mailService = mailService;
        this.testRecipient = testRecipient;
    }

    /**
     * 지정된 본인 테스트 주소로 이메일 한 통을 발송한다.
     * 발송 실패 시 예외를 전달하며 성공 응답을 반환하지 않는다.
     */
    @PostMapping("/test")
    public ApiResponse<Void> sendTestMail() {
        mailService.send(
                testRecipient,
                "[MyHome] 이메일 발송 테스트",
                "MyHome 이메일 발송 테스트입니다."
        );

        return ApiResponse.success(null);
    }
}

package com.myhome.common.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 수신 주소·제목·본문을 받아 일반 텍스트 이메일을 발송한다.
 * 발송 대상 조회와 발송 이력 관리는 호출하는 서비스에서 처리한다.
 */
@Service
public class MailService {

    private final JavaMailSender mailSender;
    private final String senderEmail;

    public MailService(
            JavaMailSender mailSender,
            @Value("${spring.mail.username}") String senderEmail
    ) {
        this.mailSender = mailSender;
        this.senderEmail = senderEmail;
    }

    /**
     * 설정된 발송 계정으로 일반 텍스트 이메일을 전송한다.
     * 인증번호 발송과 장보기 알림에서 공통으로 사용한다.
     *
     * 정상 반환은 SMTP 전송 요청이 오류 없이 끝났다는 의미이며,
     * 수신함 도착이나 사용자의 열람까지 보장하지 않는다.
     * 수신 서버가 나중에 거절하면 발송 계정으로 반송 메일이 도착할 수 있다.
     *
     * 전송 중 발생한 예외는 호출자에게 전달한다.
     * 인증 요청 무효화나 발송 실패 이력 저장은 호출한 서비스가 담당한다.
     */
    public void send(String recipient, String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(senderEmail);
        message.setTo(recipient);       // 받을 사람의 이메일
        message.setSubject(subject);    // 메일 제목
        message.setText(content);       // 메일 본문

        mailSender.send(message);
    }
}
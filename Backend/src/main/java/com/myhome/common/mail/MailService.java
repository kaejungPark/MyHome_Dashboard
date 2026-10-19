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
     * 설정된 발송 계정으로 이메일을 보낸다.
     * 발송 실패 예외는 호출한 쪽으로 전달하여 실패 이력을 기록할 수 있게 한다.
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
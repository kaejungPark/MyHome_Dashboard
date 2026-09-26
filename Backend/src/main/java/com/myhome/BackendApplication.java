package com.myhome;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Spring Boot 백엔드 애플리케이션의 시작 클래스다. */
@SpringBootApplication
public class BackendApplication {

    // Spring 애플리케이션을 초기화하고 내장 웹 서버를 실행한다.
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

}

package com.myhome.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Scheduled가 붙은 메서드를 정해진 시간에 실행하도록 활성화한다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig { }

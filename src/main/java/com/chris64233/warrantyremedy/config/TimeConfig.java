package com.chris64233.warrantyremedy.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 统一时钟入口，业务服务通过注入 {@link Clock} 获取当前时间，测试可固定时钟。
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}

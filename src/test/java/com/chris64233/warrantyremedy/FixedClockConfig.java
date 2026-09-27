package com.chris64233.warrantyremedy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 固定业务时钟：2026-01-15，保证保修期限相关测试确定性。
 */
@TestConfiguration
public class FixedClockConfig {

    public static final Clock FIXED = Clock.fixed(Instant.parse("2026-01-15T00:00:00Z"), ZoneOffset.UTC);

    @Bean
    @Primary
    public Clock fixedClock() {
        return FIXED;
    }
}

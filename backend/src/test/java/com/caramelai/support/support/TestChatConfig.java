package com.caramelai.support.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the real Google AI Studio model with a scripted fake in default (offline) test runs. */
@TestConfiguration
public class TestChatConfig {

    @Bean
    @Primary
    public FakeChatModel fakeChatModel() {
        return new FakeChatModel();
    }
}

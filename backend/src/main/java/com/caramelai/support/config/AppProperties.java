package com.caramelai.support.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Application settings under the {@code app.*} prefix. Defaults match application.yml. */
@ConfigurationProperties("app")
public record AppProperties(
        @DefaultValue Prompt prompt,
        @DefaultValue Knowledge knowledge,
        @DefaultValue Chat chat,
        @DefaultValue Agent agent,
        @DefaultValue Cors cors) {

    public record Prompt(@DefaultValue("true") boolean systemRoleSupported) {}

    public record Knowledge(
            @DefaultValue("classpath:support-content/") String path, @DefaultValue("60000") int maxChars) {}

    public record Chat(
            @DefaultValue("2000") int maxMessageChars,
            @DefaultValue("20") int historyLimit,
            @DefaultValue("30") int providerTimeoutSeconds) {}

    public record Agent(@DefaultValue("") String token) {}

    public record Cors(@DefaultValue("http://localhost:5173") String allowedOrigin) {}

    /** Programmatic defaults, used by unit tests. */
    public static AppProperties defaults() {
        return new AppProperties(
                new Prompt(true),
                new Knowledge("classpath:support-content/", 60000),
                new Chat(2000, 20, 30),
                new Agent(""),
                new Cors("http://localhost:5173"));
    }
}

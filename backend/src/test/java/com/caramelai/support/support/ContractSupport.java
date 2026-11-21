package com.caramelai.support.support;

import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import java.nio.file.Path;
import org.springframework.test.web.servlet.ResultMatcher;

/** Validates MockMvc exchanges against specs/001-support-chat-assistant/contracts/chat-api.yaml. */
public final class ContractSupport {

    private static final String SPEC = Path.of("..", "specs", "001-support-chat-assistant", "contracts", "chat-api.yaml")
            .toAbsolutePath()
            .normalize()
            .toUri()
            .toString();

    private ContractSupport() {}

    public static ResultMatcher matchesContract() {
        return OpenApiValidationMatchers.openApi().isValid(SPEC);
    }
}

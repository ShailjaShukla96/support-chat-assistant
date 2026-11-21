package com.caramelai.support.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;

/** Skips a live test unless GOOGLE_AI_STUDIO_API_KEY is set in the environment or in the repo-root .env. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(RequiresLiveKey.Condition.class)
public @interface RequiresLiveKey {

    class Condition implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            String env = System.getenv("GOOGLE_AI_STUDIO_API_KEY");
            if (env != null && !env.isBlank()) {
                return ConditionEvaluationResult.enabled("key found in environment");
            }
            for (String candidate : new String[] {"../.env", ".env"}) {
                try {
                    Path p = Path.of(candidate);
                    if (Files.exists(p) && Files.readAllLines(p).stream().anyMatch(l ->
                            l.startsWith("GOOGLE_AI_STUDIO_API_KEY=")
                                    && l.length() > "GOOGLE_AI_STUDIO_API_KEY=".length()
                                    && !l.endsWith("your-key-here"))) {
                        return ConditionEvaluationResult.enabled("key found in " + candidate);
                    }
                } catch (Exception ignored) {
                    // fall through to disabled
                }
            }
            return ConditionEvaluationResult.disabled("GOOGLE_AI_STUDIO_API_KEY not set: live suite skipped");
        }
    }
}

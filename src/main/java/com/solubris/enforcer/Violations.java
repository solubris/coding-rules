package com.solubris.enforcer;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import java.text.MessageFormat;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

public class Violations {
    private Violations() {
    }

    public static void throwViolations(Stream<String> violations, String titleFormat) throws EnforcerRuleException {
        LongAdder count = new LongAdder();
        String body = violations
                .peek(v -> count.increment())
                .map(v -> "  - " + v)
                .collect(joining("\n", "", "\n"));
        if (count.longValue() > 0) {
            String title = MessageFormat.format(titleFormat, count.sum());
            throw new EnforcerRuleException(title + "\n" + body);
        }
    }
}

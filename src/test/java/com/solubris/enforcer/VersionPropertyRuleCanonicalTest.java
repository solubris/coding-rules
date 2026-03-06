package com.solubris.enforcer;

import org.apache.maven.enforcer.rule.api.EnforcerLogger;
import org.apache.maven.model.Model;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static com.solubris.enforcer.ModelStubber.dependencyOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

@Disabled
public class VersionPropertyRuleCanonicalTest {
    /**
     * TODO - canonicalization logic treats literals as properties if a property with the same value exists.
     */
    @Test
    public void canonicalizationTreatsLiteralsAsPropertyIfValueMatches() throws Exception {
        Model model = new Model();
        model.addProperty("pmd.version", "7.19.0");
        model.addDependency(dependencyOf("pmd", "core", "7.19.0"));
        model.addDependency(dependencyOf("pmd", "test", "7.19.0"));

        VersionPropertyRule rule = new VersionPropertyRule(model, null);
        rule.setLog(mock(EnforcerLogger.class));

        Stream<String> violations = rule.scanProperties();

        // Assert that we do not get a literal-duplicate violation because property exists with same value
        boolean hasLiteralDuplicate = violations.anyMatch(s -> s.contains("appears"));
        assertFalse(hasLiteralDuplicate);
    }
}

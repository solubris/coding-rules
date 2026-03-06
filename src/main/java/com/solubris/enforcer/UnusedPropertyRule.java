package com.solubris.enforcer;

import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * Ensures that version properties are used.
 *
 * <p>This catches the typical scenario where a dependency is removed, but the property left orphaned.
 */
@Named("unusedPropertyRule")
public class UnusedPropertyRule extends AbstractEnforcerRule {
    private static final Pattern PROPERTY_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    protected static final String SUPPRESSIONS_PROPERTY = "unusedPropertyRule.suppressions";

    private final Model originalModel;
    private final Model effectiveModel;

    @SuppressWarnings("unused")
    @Inject
    public UnusedPropertyRule(MavenSession session) {
        this(ModelScanner.modelFrom(session), session.getCurrentProject().getModel());
    }

    protected UnusedPropertyRule(Model originalModel, Model effectiveModel) {
        this.originalModel = originalModel;
        this.effectiveModel = effectiveModel;
    }

    @Override
    public void execute() throws EnforcerRuleException {
        LongAdder count = new LongAdder();
        String message = scanProperties()
                .map(v -> "  - " + v)
                .peek(v -> count.increment())
                .collect(joining("\n", "Version property violations found:\n", "\n"));
        if (count.longValue() > 0) {
            throw new EnforcerRuleException(message);
        }
    }

    protected Stream<String> scanProperties() {
        Map<String, List<ArtifactV2>> byKey = ModelScanner.scanModel(effectiveModel)
                .collect(Collectors.groupingBy(ArtifactV2::fullKey, toList()));
        Stream<ArtifactV2> resolved = ModelScanner.scanModel(originalModel)
                .map(a -> a.withEffectiveVersion(byKey.get(a.fullKey()).get(0).getVersion()));

        Map<String, List<ArtifactV2>> byVersion = resolved
//                .filter(a -> a.getEffectiveVersion() != null)
//                .filter(artifact -> !isExcluded(artifact.getVersion()))
                .collect(groupingBy(ArtifactV2::getVersion, toList()));

        String property = originalModel.getProperties().getProperty(SUPPRESSIONS_PROPERTY);
        Set<String> suppressed = parseSuppressedPropertyList(property);

        return originalModel.getProperties().entrySet().stream()
                .filter(UnusedPropertyRule::isVersionProperty) // only check properties that look like versions
                .filter(e -> !suppressed.contains(e.getKey().toString()))
                .map(e -> {
                    String propName = e.getKey().toString();
                    String propValue = e.getValue() != null ? e.getValue().toString() : "";
                    List<ArtifactV2> artifacts = byVersion.getOrDefault("${" + propName + "}", Collections.emptyList());
                    return artifacts.isEmpty() ? unusedUseViolation(propName, propValue) : null;
                }).filter(Objects::nonNull);
    }

    private static Set<String> parseSuppressedPropertyList(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptySet();
        return Arrays.stream(raw.split("[,\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private static boolean isVersionProperty(Map.Entry<Object, Object> e) {
        return e.getKey().toString().endsWith(".version");
    }

    /**
     * TODO properties could be unused because they only override a parent property.
     * Overriding a parent will actually override the version of that dependency,
     * so its a valid use of a property.
     * Perhaps could also check the parent's for mention of the property.
     * If it's not used in the parents, then it's definitely unused.
     *
     * <p>Another approach could be to just log a warning in this case.
     */
    private static String unusedUseViolation(String propName, String propValue) {
        return String.format(
                "Version property '${%s}' (value: %s) is unused. " +
                        "Check if it overrides a property from the parent, otherwise remove it.",
                propName, propValue);
    }
}

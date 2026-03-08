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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.solubris.enforcer.ModelScanner.modelFrom;
import static com.solubris.enforcer.ModelScanner.scanModel;
import static com.solubris.enforcer.Violations.throwViolations;
import static java.util.Collections.emptyList;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toUnmodifiableSet;

/**
 * Ensures that version properties are used.
 *
 * <p>This catches the typical scenario where a dependency is removed, but the property is left orphaned.
 *
 * <p>TODO What if there is a version inside a configuration block?
 */
@Named("unusedPropertyRule")
public class UnusedPropertyRule extends AbstractEnforcerRule {
    protected static final String SUPPRESSIONS_PROPERTY = "unusedPropertyRule.suppressions";

    private final Model originalModel;
    private final Model effectiveModel;

    protected String includesPattern = ".*\\.version";
    protected String excludesPattern = "project\\..*";

    @SuppressWarnings("unused")
    @Inject
    public UnusedPropertyRule(MavenSession session) {
        this(modelFrom(session), session.getCurrentProject().getModel());
    }

    protected UnusedPropertyRule(Model originalModel, Model effectiveModel) {
        this.originalModel = originalModel;
        this.effectiveModel = effectiveModel;
    }

    @Override
    public void execute() throws EnforcerRuleException {
        throwViolations(scan(), "Found {0} unused property violation(s):");
    }

    protected Stream<String> scan() {
        Map<String, List<Artifact>> byKey = scanModel(effectiveModel)
                .collect(groupingBy(Artifact::fullKey, toList()));   // TODO just want a single item
        Map<String, List<Artifact>> byVersion = scanModel(originalModel)
                .map(a -> a.withEffectiveVersion(byKey.get(a.fullKey()).get(0).getVersion()))
                .collect(groupingBy(Artifact::getVersion, toList()));

        Set<String> suppressed = extractSuppressions(originalModel);

        return originalModel.getProperties().entrySet().stream()
                .map(UnusedPropertyRule::asStringEntry)
                .filter(this::isIncluded)
                .filter(not(this::isExcluded))
                .filter(e -> !suppressed.contains(e.getKey()))
                .map(e -> {
                    String propName = e.getKey();
                    List<Artifact> artifacts = byVersion.getOrDefault("${" + propName + "}", emptyList());
                    return artifacts.isEmpty() ? unusedUseViolation(propName, e.getValue()) : null;
                }).filter(Objects::nonNull);
    }

    private static Map.Entry<String, String> asStringEntry(Map.Entry<Object, Object> e) {
        return Map.entry(e.getKey().toString(), e.getValue() != null ? e.getValue().toString() : "");
    }

    private static Set<String> extractSuppressions(Model model) {
        String raw = model.getProperties().getProperty(SUPPRESSIONS_PROPERTY);
        if (raw == null || raw.isBlank()) return Collections.emptySet();
        return Arrays.stream(raw.split("[,\\s]+"))
                .map(String::trim)
                .filter(not(String::isEmpty))
                .collect(toUnmodifiableSet());
    }

    /**
     * TODO should maven properties like ${project.version} be considered here?
     * project.version is typically defined in the version element, so why would it be defined in the properties?
     * Using a regex could cover both cases.
     */
    private boolean isIncluded(Map.Entry<String, String> e) {
        // TODO these patterns could be precompiled and stored as fields instead of being recompiled for every property
        if (includesPattern == null || includesPattern.isBlank()) return false;
        Pattern includes = Pattern.compile(includesPattern);
        Matcher includesMatcher = includes.matcher(e.getKey());
        return includesMatcher.matches();
    }

    private boolean isExcluded(Map.Entry<String, String> e) {
        if (excludesPattern == null || excludesPattern.isBlank()) return false;
        Pattern excludes = Pattern.compile(excludesPattern);
        Matcher excludesMatcher = excludes.matcher(e.getKey());
        return excludesMatcher.matches();
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
                "Version property %s=%s is unused. " +
                        "Check if it overrides a property from the parent, otherwise remove it.",
                propName, propValue);
    }
}

package com.solubris.enforcer;

import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.solubris.enforcer.ModelScanner.modelFrom;
import static com.solubris.enforcer.ModelScanner.scanModel;
import static com.solubris.enforcer.PropertyUtil.fromPlaceHolder;
import static com.solubris.enforcer.Violations.throwViolations;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * Ensures that version properties are used only when a version appears more than once in the POM.
 *
 * <p>Enforces that:
 * <ul>
 * <li>If a version string appears only once, it should be hardcoded</li>
 * <li>If a version string appears multiple times, it MUST be defined as a
 * property and referenced</li>
 * </ul>
 *
 * <p>This prevents unnecessary property definitions and improves POM readability.
 */
@Named("versionPropertyRule")
public class VersionPropertyRule extends AbstractEnforcerRule {
    private static final Pattern PROPERTY_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    private final Model originalModel;
    private final Model effectiveModel;

    protected boolean allowSingleUseOfProperty;
    protected boolean requirePropertiesForDuplicates = true;
    protected boolean ignoreParentVersion = true;
    protected List<String> excludeVersions = new ArrayList<>();

    @SuppressWarnings("unused")
    @Inject
    public VersionPropertyRule(MavenSession session) {
        this(modelFrom(session), session.getCurrentProject().getModel());
    }

    protected VersionPropertyRule(Model originalModel, Model effectiveModel) {
        this.originalModel = originalModel;
        this.effectiveModel = effectiveModel;
    }

    @Override
    public void execute() throws EnforcerRuleException {
        throwViolations(scan(), "Found {0} version violation(s):");
    }

    @SuppressWarnings("PMD.CollapsibleIfStatements")
    protected Stream<String> scan() {
        Map<String, List<Artifact>> byKey = scanModel(effectiveModel)
                .collect(groupingBy(Artifact::fullKey, toList()));
        Map<String, List<Artifact>> usages = scanModel(originalModel)
                .map(a -> a.withEffectiveVersion(fetchVersion(a, byKey)))
                .filter(a -> a.getEffectiveVersion() != null)
                .filter(artifact -> !isExcluded(artifact.getVersion()))
                .collect(groupingBy(Artifact::getEffectiveVersion, toList()));

        // byVersion maps from property to list of artifacts
        // now can go through property keys and check usage

        return usages.entrySet().stream()
                .map(e -> {
                    String effectiveVersion = e.getKey();
                    List<Artifact> artifacts = e.getValue();
                    long propertyCount = artifacts.stream()
                            .filter(Artifact::hasImplicitVersion)
                            .count();
                    if (artifacts.size() > 1) {
                        if (propertyCount == 0) return missingPropertyViolation(effectiveVersion, artifacts);
                        if (propertyCount < artifacts.size()) return unusedPropertyViolation(effectiveVersion, artifacts);
                    } else if (artifacts.size() == 1) {
                        if (propertyCount == 1) return redundantPropertyViolation(artifacts.get(0));
                    }
                    return null;
                }).filter(Objects::nonNull);
    }

    private String fetchVersion(Artifact a, Map<String, List<Artifact>> byKey) {
        List<Artifact> artifacts = byKey.getOrDefault(a.fullKey(), List.of());
        if (!artifacts.isEmpty()) return artifacts.get(0).getVersion();
        getLog().warn("Could not find artifact in effectiveModel for " + a.fullKey());
        return null;
    }

    private String redundantPropertyViolation(Artifact artifact) {
        if (allowSingleUseOfProperty) return null;

        return String.format(
                "Version property %s=%s is only used once. Please inline the property version.",
                fromPlaceHolder(artifact.getVersion()), artifact.getEffectiveVersion());
    }

    /**
     * The artifacts could either have the explicit version or a reference to the property.
     * Where the property represents the same version.
     * If the property uses a different version than the explicit version,
     * then that would be detected at the moment - could be a different violation (property value doesn't match usage).
     *
     * <p>Could the artifacts refer to different properties that have the same value?
     * That's possible due to coincidental properties - another edge case to consider.
     */
    private static String unusedPropertyViolation(String effectiveVersion, List<Artifact> artifacts) {
        String unused = artifacts.stream()
                .filter(Artifact::hasExplicitVersion)
                .map(Artifact::key)
                .collect(joining(", "));
        String propertyName = artifacts.stream()
                .filter(Artifact::hasImplicitVersion)
                .map(Artifact::getVersion)
                .map(PropertyUtil::fromPlaceHolder)
                .findAny().orElse("unknown");
        return String.format(
                "Version property %s=%s exists but is not used everywhere. Unused locations: %s",
                propertyName, effectiveVersion, unused);
    }

    private String missingPropertyViolation(String version, List<Artifact> artifacts) {
        if (!requirePropertiesForDuplicates) return null;

        String unused = artifacts.stream()
                .filter(a -> Objects.equals(a.getVersion(), a.getEffectiveVersion()))
                .map(Artifact::key)
                .collect(joining(", "));
        return String.format(
                "Version '%s' exists in multiple locations, please extract a version property. " +
                        "Unused locations: %s",
                version, unused);
    }

    /**
     * Check if a version should be excluded from checks.
     *
     * @param version the version string to check
     * @return true if the version should be excluded, false otherwise
     */
    @SuppressWarnings("checkstyle:InvertedControlFlow")
    private boolean isExcluded(String version) {
        if (version == null) return true;
        if (excludeVersions.contains(version)) return true;

        if (ignoreParentVersion && Objects.equals(version, "${project.parent.version}")) {
            return true;
        }

        // Match version ranges like [1.0,2.0) or (1.0,2.0]
        // noinspection RegExpRedundantEscape
        return version.matches("[\\[\\(].*[\\]\\)]");
    }
}

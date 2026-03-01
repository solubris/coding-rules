package com.solubris.enforcer;

import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.*;
import org.apache.maven.project.MavenProject;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;

/**
 * Maven Enforcer Rule: VersionPropertyRule.
 *
 * <p>
 * Ensures that version properties are used only when a version appears more
 * than once in the POM. Enforces that:
 * </p>
 * <ul>
 * <li>If a version string appears only once, it should be hardcoded</li>
 * <li>If a version string appears multiple times, it MUST be defined as a
 * property and referenced</li>
 * </ul>
 *
 * <p>
 * This prevents unnecessary property definitions and improves POM readability.
 * </p>
 *
 * @author Maven Enforcer Plugin
 * @see <a href="https://maven.apache.org/enforcer/enforcer-api/writing-a-custom-rule.html">
 * Writing a Custom Rule</a>
 */
@Named("versionPropertyRule")
public class VersionPropertyRule extends AbstractEnforcerRule {
    private static final Pattern PROPERTY_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    private final Model model;

    private boolean requirePropertiesForDuplicates = true;
    private boolean ignoreParentVersion = true;
    private List<String> excludeVersions = new ArrayList<>();

    @Inject
    public VersionPropertyRule(MavenSession session) {
        this(modelFrom(session));
    }

    private static Model modelFrom(MavenSession session) {
        // Use original model to avoid implicit/default plugins and dependencies
        // that Maven adds automatically (e.g., default compiler plugin)
        MavenProject project = session.getCurrentProject();
        Model originalModel = project.getOriginalModel();
        return originalModel != null ? originalModel : project.getModel();
    }

    protected VersionPropertyRule(Model model) {
        this.model = model;
    }

    @Override
    public void execute() throws EnforcerRuleException {
        Stream<String> violations = scanAll();

        LongAdder count = new LongAdder();
        String message = violations
                .map(v -> "  - " + v)
                .peek(v -> count.increment())
                .collect(Collectors.joining("\n", "Version property violations found:\n", "\n"));
        if (count.longValue() > 0) {
            throw new EnforcerRuleException(message);
        }
    }

    protected Stream<String> scanAll() {
        // TODO scan plugin dependencies
        Stream.Builder<Stream<Artifact>> result = Stream.builder();
        result.add(scanDependencies(directDependencies(model), "direct-dependency"));
        result.add(scanDependencies(managedDependencies(model), "managed-dependency"));
        result.add(scanPlugins(directPlugins(model), "direct-plugin"));
        result.add(scanPlugins(managedPlugins(model), "managed-plugin"));
        result.add(scanProfiles(model));

        Map<String, List<String>> versionLocations = result.build()
                .flatMap(Function.identity())
                .filter(artifact -> !isExcluded(artifact.getVersion()))
                .collect(groupingBy(Artifact::getVersion, Collectors.mapping(Artifact::toString, Collectors.toList())));

        return checkVersionPropertyUsage(versionLocations);
    }

    private static Stream<Dependency> directDependencies(ModelBase model) {
        return Optional.ofNullable(model.getDependencies())
                .stream()
                .flatMap(Collection::stream);
    }

    private static Stream<Dependency> managedDependencies(ModelBase model) {
        return Optional.ofNullable(model.getDependencyManagement())
                .map(DependencyManagement::getDependencies)
                .stream()
                .flatMap(Collection::stream);
    }

    private static Stream<Plugin> managedPlugins(Model model) {
        return Optional.ofNullable(model.getBuild())
                .map(BuildBase::getPluginManagement)
                .map(PluginManagement::getPlugins)
                .stream()
                .flatMap(Collection::stream);
    }

    private static Stream<Plugin> managedPlugins(Profile model) {
        return Optional.ofNullable(model.getBuild())
                .map(BuildBase::getPluginManagement)
                .map(PluginManagement::getPlugins)
                .stream()
                .flatMap(Collection::stream);
    }

    private static Stream<Plugin> directPlugins(Model model) {
        return Optional.ofNullable(model.getBuild())
                .map(BuildBase::getPlugins)
                .stream()
                .flatMap(Collection::stream);
    }

    private Stream<Plugin> directPlugins(Profile profile) {
        return Optional.ofNullable(profile.getBuild())
                .map(BuildBase::getPlugins)
                .stream()
                .flatMap(Collection::stream);
    }

    private Stream<Artifact> scanProfiles(Model model) {
        if (model.getProfiles() == null) return Stream.empty();

        Stream.Builder<Stream<Artifact>> result = Stream.builder();
        for (Profile profile : model.getProfiles()) {
            result.add(scanDependencies(directDependencies(model), profile.getId() + "-direct-dependency"));
            result.add(scanDependencies(managedDependencies(model), profile.getId() + "-managed-dependency"));
            result.add(scanDependencies(directDependencies(profile), profile.getId() + "-direct-dependency"));
            result.add(scanDependencies(managedDependencies(profile), profile.getId() + "-managed-dependency"));
            result.add(scanPlugins(directPlugins(profile), profile.getId() + "-direct-plugin"));
            result.add(scanPlugins(managedPlugins(profile), profile.getId() + "-managed-plugin"));
        }

        return result.build().flatMap(Function.identity());
    }

    private Stream<Artifact> scanDependencies(Stream<Dependency> dependencies, String type) {
        return dependencies
                .map(dep -> new Artifact(dep.getVersion(), dep.getArtifactId(), dep.getGroupId(), type));
    }

    private Stream<Artifact> scanPlugins(Stream<Plugin> plugins, String type) {
        return plugins
                .map(dep -> new Artifact(dep.getVersion(), dep.getArtifactId(), dep.getGroupId(), type));
    }

    /**
     * Check for version property usage violations.
     *
     * <p>This implementation normalizes versions to a canonical key so that
     * occurrences that use a property (e.g. ${pmd.version}) and occurrences
     * that use the literal value (e.g. 7.19.0) are treated as the same
     * version when the literal equals a declared property value. This avoids
     * false positives where the effective value comes from a property but the
     * model contains mixed references.
     *
     * @return list of violation messages
     */
    private Stream<String> checkVersionPropertyUsage(Map<String, List<String>> versionLocations) {
        Map<String, Integer> versionCounts
                = versionLocations.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size()));

        List<String> violations = new ArrayList<>();

        // Gather declared properties (use effective project properties so inherited
        // properties are included)
        Map<String, String> definedProperties = new HashMap<>();
        if (model.getProperties() != null) {
            model.getProperties().forEach((k, v) ->
                    definedProperties.put(k.toString(), v != null ? v.toString() : ""));
        }

        // Build reverse map from property value -> property name (first one wins)
        Map<String, String> valueToProperty = new HashMap<>();
        for (Map.Entry<String, String> e : definedProperties.entrySet()) {
            valueToProperty.putIfAbsent(e.getValue(), e.getKey());
        }

        // Canonicalize versions: map raw version string -> canonical key
        // canonical key format:
        //  - "prop:<name>" when a property is (or should be) used
        //  - "ver:<literal>" for plain literals with no matching property
        Map<String, Integer> canonicalCounts = new HashMap<>();
        Map<String, String> canonicalRepresentative = new HashMap<>();

        for (Map.Entry<String, Integer> entry : versionCounts.entrySet()) {
            String version = entry.getKey();
            int count = entry.getValue();

            Matcher matcher = PROPERTY_PATTERN.matcher(version);
            String canonical;
            if (matcher.matches()) {
                String propertyName = matcher.group(1);
                canonical = "prop:" + propertyName;
                canonicalRepresentative.putIfAbsent(canonical, "${" + propertyName + "}");
            } else if (valueToProperty.containsKey(version)) {
                // Literal equals a declared property value -> treat as that property
                String propName = valueToProperty.get(version);
                canonical = "prop:" + propName;
                canonicalRepresentative.putIfAbsent(canonical, "${" + propName + "}");
            } else {
                canonical = "ver:" + version;
                canonicalRepresentative.putIfAbsent(canonical, version);
            }

            canonicalCounts.put(canonical, canonicalCounts.getOrDefault(canonical, 0) + count);
        }

        // Now evaluate violations based on canonical counts
        for (Map.Entry<String, Integer> entry : canonicalCounts.entrySet()) {
            String key = entry.getKey();
            int count = entry.getValue();

            getLog().debug("[VersionPropertyRule DEBUG] canonical='" + key + "' count=" + count + " repr='" + canonicalRepresentative.get(key) + "' locations='" + versionLocations.getOrDefault(canonicalRepresentative.get(key), java.util.Collections.emptyList()) + "'");

            if (key.startsWith("ver:")) {
                String literal = canonicalRepresentative.get(key);
                List<String> locs = versionLocations.getOrDefault(literal, java.util.Collections.emptyList());
                if (count > 1 && requirePropertiesForDuplicates) {
                    violations.add(String.format(
                            "Version '%s' appears %d times but is not using a property. Create a property and reference it in all occurrences. Locations: %s",
                            literal, count, locs));
                }
            } else if (key.startsWith("prop:")) {
                String propName = key.substring(5);
                int occurrences = entry.getValue();
                // If the property is only used once (even if declared), it's redundant
                if (occurrences == 1) {
                    String propValue = definedProperties.get(propName);
                    violations.add(String.format(
                            "Version property '${%s}' (value: %s) is used but appears only once. " +
                                    "Remove the property and use the version directly, or ensure it's used in multiple places.",
                            propName, propValue != null ? propValue : "?"));
                }
            }
        }

        return violations.stream();
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

    /**
     * Set whether properties are required for duplicate versions.
     *
     * @param requirePropertiesForDuplicates true if required
     */
    public void setRequirePropertiesForDuplicates(
            boolean requirePropertiesForDuplicates) {
        this.requirePropertiesForDuplicates = requirePropertiesForDuplicates;
    }

    /**
     * Set whether to ignore parent version references.
     *
     * @param ignoreParentVersion true if parent version should be ignored
     */
    @SuppressWarnings("unused")
    public void setIgnoreParentVersion(boolean ignoreParentVersion) {
        this.ignoreParentVersion = ignoreParentVersion;
    }

    /**
     * Set the list of versions to exclude from checks.
     *
     * @param excludeVersions list of version strings to exclude
     */
    @SuppressWarnings("unused")
    public void setExcludeVersions(List<String> excludeVersions) {
        this.excludeVersions = excludeVersions != null ? excludeVersions : new ArrayList<>();
    }
}

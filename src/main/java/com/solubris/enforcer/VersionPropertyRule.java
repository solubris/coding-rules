package com.solubris.enforcer;

import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.BuildBase;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.ModelBase;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Reporting;
import org.apache.maven.project.MavenProject;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.solubris.StreamSugar.prepend;
import static java.util.function.Function.identity;
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

    private final Model model;
    private final MavenProject project;

    private boolean requirePropertiesForDuplicates = true;
    private boolean ignoreParentVersion = true;
    private List<String> excludeVersions = new ArrayList<>();

    @SuppressWarnings("unused")
    @Inject
    public VersionPropertyRule(MavenSession session) {
        this(modelFrom(session), session.getCurrentProject());
    }

    protected VersionPropertyRule(Model model, MavenProject project) {
        this.model = model;
        this.project = project;
    }

    private static Model modelFrom(MavenSession session) {
        // Use original model to avoid implicit/default plugins and dependencies
        // that Maven adds automatically (e.g., default compiler plugin)
        MavenProject project = session.getCurrentProject();
        Model originalModel = project.getOriginalModel();
        return originalModel != null ? originalModel : project.getModel();
    }

    @Override
    public void execute() throws EnforcerRuleException {
        LongAdder count = new LongAdder();
        String message = Stream.concat(scanProperties(), scanUsages())
                .map(v -> "  - " + v)
                .peek(v -> count.increment())
                .collect(joining("\n", "Version property violations found:\n", "\n"));
        if (count.longValue() > 0) {
            throw new EnforcerRuleException(message);
        }
    }

    protected Stream<String> scanProperties() {
        Map<String, List<Artifact>> byKey = scanModel(project.getModel())
                .collect(Collectors.groupingBy(Artifact::fullKey, toList()));
        Stream<Artifact> resolved = scanModel(model)
                .map(a -> a.withEffectiveVersion(byKey.get(a.fullKey()).get(0).getVersion()));

        Map<String, List<Artifact>> byVersion = resolved
//                .filter(a -> a.getEffectiveVersion() != null)
//                .filter(artifact -> !isExcluded(artifact.getVersion()))
                .collect(groupingBy(Artifact::getVersion, toList()));

        return model.getProperties().entrySet().stream()
                .filter(e -> e.getKey().toString().endsWith(".version")) // only check properties that look like versions
                .map(e -> {
                    String propName = e.getKey().toString();
                    String propValue = e.getValue() != null ? e.getValue().toString() : "";
                    List<Artifact> artifacts = byVersion.getOrDefault("${" + propName + "}", Collections.emptyList());
                    if (artifacts.size() == 0) {
                        return unusedUseViolation(propName, propValue);
                    } else if (artifacts.size() == 1) {
                        // property is only used once, but there may be other occurrences of the same version that don't use the property
                        // XXX is this check covered by scanUsages()?
                        // find the usages of the literal version -
                        artifacts = byVersion.getOrDefault(propValue, Collections.emptyList());
                        if (artifacts.isEmpty()) return singleUseViolation(propName, propValue);
                        // if there are multiple occurrences of the same version, then it would be picked up by scanUsages()
                        // don't like this branch - maybe it is covered by scanUsages() and we can remove it from here?
                        // Which means that scanProperties() is only for unused properties
                        // And I like this - maybe suitable as a separate rule - UnusedVersionPropertyRule
                        // How to know if it is covered by scanUsages()
                        // property is used only once
                        // and either there are no other occurrences of the same version - scanUsages() - redundantPropertyViolation() should cover it
                        // or there are other occurrences but they all use the property - covered by scanUsages()
                        // so yes, can remove this branch
                    }
                    return null;
                }).filter(Objects::nonNull);
    }


    protected Stream<String> scanUsages() {
        Map<String, List<Artifact>> byKey = scanModel(project.getModel())
                .collect(Collectors.groupingBy(Artifact::fullKey, toList()));
        Map<String, List<Artifact>> usages = scanModel(model)
                .map(a -> a.withEffectiveVersion(byKey.get(a.fullKey()).get(0).getVersion()))
                .filter(a -> a.getEffectiveVersion() != null)
                .filter(artifact -> !isExcluded(artifact.getVersion()))
                .collect(groupingBy(Artifact::getEffectiveVersion, toList()));


        // byVersion maps from property to list of artifacts
        // now can go through property keys and check usage

        return usages.entrySet().stream()
                .map(e -> {
                    String version = e.getKey();
                    List<Artifact> artifacts = e.getValue();
                    long propertyCount = artifacts.stream()
                            .filter(a -> !Objects.equals(a.getVersion(), a.getEffectiveVersion()))
                            .count();
                    if (artifacts.size() > 1) {
                        if (propertyCount == 0) return missingPropertyViolation(version, artifacts);
                        if (propertyCount < artifacts.size()) return unusedPropertyViolation(version, artifacts);
                    } else if (artifacts.size() == 1) {
                        if (propertyCount == 1) return redundantPropertyViolation(version, artifacts.get(0));
                    }
                    return null;
                }).filter(Objects::nonNull);
    }

    private static Stream<Artifact> scanModel(Model model) {
        Stream.Builder<Stream<Artifact>> result = Stream.builder();
        result.add(directDependencies(model));
        result.add(managedDependencies(model));
        result.add(scanPlugins(directPlugins(model), false, null));
        result.add(scanPlugins(managedPlugins(model), true, null));
        result.add(reportPlugins(model));
        result.add(extensions(model));
        result.add(scanProfiles(model));
        return result.build()
                .flatMap(identity());
    }

    private static Stream<Artifact> directDependencies(ModelBase model) {
        return Optional.ofNullable(model.getDependencies())
                .stream()
                .flatMap(Collection::stream)
                .map(Artifact::direct);
    }

    private static Stream<Artifact> managedDependencies(ModelBase model) {
        return Optional.ofNullable(model.getDependencyManagement())
                .map(DependencyManagement::getDependencies)
                .stream()
                .flatMap(Collection::stream)
                .map(Artifact::managed);
    }

    private static Stream<Artifact> extensions(Model model) {
        return Optional.ofNullable(model.getBuild())
                .map(Build::getExtensions)
                .stream()
                .flatMap(Collection::stream)
                .map(Artifact::new);
    }

    private static Stream<Artifact> reportPlugins(ModelBase model) {
        return Optional.ofNullable(model.getReporting())
                .map(Reporting::getPlugins)
                .stream()
                .flatMap(Collection::stream)
                .map(Artifact::new);
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

    private static Stream<Plugin> directPlugins(Profile profile) {
        return Optional.ofNullable(profile.getBuild())
                .map(BuildBase::getPlugins)
                .stream()
                .flatMap(Collection::stream);
    }

    private static Stream<Dependency> pluginDependencies(Plugin plugin) {
        return Optional.ofNullable(plugin.getDependencies())
                .stream()
                .flatMap(Collection::stream);
    }

    private static Stream<Artifact> scanProfiles(Model model) {
        if (model.getProfiles() == null) return Stream.empty();

        return model.getProfiles().stream()
                .flatMap(profile -> {
                    // TODO what if the property is in the profile?
                    String profileId = profile.getId();
                    Stream.Builder<Stream<Artifact>> result = Stream.builder();
                    result.add(directDependencies(profile));
                    result.add(managedDependencies(profile));
                    result.add(scanPlugins(directPlugins(profile), false, profileId));
                    result.add(scanPlugins(managedPlugins(profile), true, profileId));
                    result.add(reportPlugins(profile));
                    return result.build()
                            .flatMap(identity())
                            .map(a -> a.withProfile(profileId));
                });
    }

    /**
     * TODO Should plugin dependencies show as a different type than regular dependencies?
     * TODO Consider whether plugin dependencies should be considered "managed" when the plugin is managed.
     */
    private static Stream<Artifact> scanPlugins(Stream<Plugin> items, boolean managed, String profile) {
        return items.flatMap(p ->
                prepend(new Artifact(p, managed, profile), pluginDependencies(p).map(d -> new Artifact(d, managed, profile)))
        );
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
    private Stream<String> checkUsage(Map<String, List<String>> versionLocations) {
        Map<String, Integer> versionCounts
                = versionLocations.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size()));

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
            } else if (valueToProperty.containsKey(version)) {  // but what two properties have the same value? we take the first one
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

        return canonicalCounts.entrySet().stream()
                .peek(e -> logArtefactVersion(e.getKey(), e.getValue(), canonicalRepresentative.get(e.getKey()), versionLocations))
                .map(entry -> {
                    String key = entry.getKey();
                    if (key.startsWith("ver:")) {
                        return checkExplicitVersion(versionLocations, entry.getValue(), canonicalRepresentative, key);
                    } else if (key.startsWith("prop:")) {
                        return checkProperty(key, definedProperties, entry.getValue());
                    }
                    return null;
                }).filter(Objects::nonNull);
    }

    private static String checkProperty(String key, Map<String, String> definedProperties, int count) {
        if (count > 1) return null;

        String propName = key.substring(5);
        String propValue = definedProperties.getOrDefault(propName, "?");
        return singleUseViolation(propName, propValue);
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

    private static String singleUseViolation(String propName, String propValue) {
        return String.format(
                "Version property '${%s}' (value: %s) is used but appears only once. " +
                        "Remove the property and use the version directly, or ensure it's used in multiple places.",
                propName, propValue);
    }

    private static String redundantPropertyViolation(String version, Artifact artifact) {
        return String.format(
                "Version property '${%s}' (value: %s) is only used once. " +
                        "Please inline the property version.",
                artifact.getVersion(), artifact.getEffectiveVersion());
    }

    private static String unusedPropertyViolation(String version, List<Artifact> artifacts) {
        String unused = artifacts.stream()
                .filter(a -> Objects.equals(a.getVersion(), a.getEffectiveVersion()))
                .map(Artifact::key)
                .collect(joining(", "));
        return String.format(
                "Version property '${%s}' (value: %s) exists but it not used everywhere. " +
                        "Unused locations: %s",
                version, version, unused);  // TODO version is not right here
    }

    private static String missingPropertyViolation(String version, List<Artifact> artifacts) {
        String unused = artifacts.stream()
                .filter(a -> Objects.equals(a.getVersion(), a.getEffectiveVersion()))
                .map(Artifact::key)
                .collect(joining(", "));
        return String.format(
                "Version '${%s}' exists in multiple locations, please extract a version property. " +
                        "Unused locations: %s",
                version, unused);
    }



    private String checkExplicitVersion(Map<String, List<String>> versionLocations, int count, Map<String, String> canonicalRepresentative, String key) {
        if (count > 1 && requirePropertiesForDuplicates) {
            String literal = canonicalRepresentative.get(key);
            List<String> locs = versionLocations.getOrDefault(literal, Collections.emptyList());
            return String.format(
                    "Version '%s' appears %d times but is not using a property. Create a property and reference it in all occurrences. Locations: %s",
                    literal, count, locs);
        }
        return null;
    }

    private void logArtefactVersion(String key, Integer count, String representative, Map<String, List<String>> versionLocations) {
        List<String> orDefault = versionLocations.getOrDefault(representative, Collections.emptyList());
        getLog().debug("[VersionPropertyRule DEBUG] canonical='" + key + "' count=" + count + " repr='" + representative + "' locations='" + orDefault + "'");
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

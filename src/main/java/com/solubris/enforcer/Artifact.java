package com.solubris.enforcer;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.With;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.ReportPlugin;

import java.util.Objects;

@Builder(toBuilder = true, setterPrefix = "with")
@RequiredArgsConstructor
@Getter
public class Artifact {
    private final String version;
    private final String artifactId;
    private final String groupId;
    private final String type;      // dependency, plugin, parent, etc.
    private final boolean managed;  // whether the version is from dependencyManagement or pluginManagement
    @With
    private final String profile;
    @With
    private final String effectiveVersion; // version after resolution, may be null if not resolved yet

    public Artifact(Dependency dependency, boolean managed, String profile) {
        this(dependency.getVersion(), dependency.getArtifactId(), dependency.getGroupId(), "Dependency", managed, profile, null);
    }

    public Artifact(Plugin plugin, boolean managed, String profile) {
        this(plugin.getVersion(), plugin.getArtifactId(), plugin.getGroupId(), "Plugin", managed, profile, null);
    }

    public Artifact(ReportPlugin plugin) {
        this(plugin.getVersion(), plugin.getArtifactId(), plugin.getGroupId(), "ReportPlugin", false, null, null);
    }

    public Artifact(ReportPlugin plugin, String profile) {
        this(plugin.getVersion(), plugin.getArtifactId(), plugin.getGroupId(), "ReportPlugin", false, profile, null);
    }

    public Artifact(Extension extension) {
        this(extension.getVersion(), extension.getArtifactId(), extension.getGroupId(), "Extension", false, null, null);
    }

    @Override
    public String toString() {
        String profilePart = profile != null ? String.format(" (profile: %s)", profile) : "";
        return String.format("%s: %s:%s(%s)%s", type, groupId, artifactId, managed ? "managed" : "direct", profilePart);
    }

    public String fullKey() {
        String profilePart = profile != null ? profile : "";
        return String.format("%s:%s:%s:%s:%s", type, groupId, artifactId, managed ? "managed" : "direct", profilePart);
    }

    public static Artifact direct(Dependency dependency) {
        return new Artifact(dependency, false, null);
    }

    public static Artifact managed(Dependency dependency) {
        return new Artifact(dependency, true, null);
    }

    public String key() {
        // TODO can use versionlessKey()
        return groupId + ":" + artifactId;
    }

    public boolean hasExplicitVersion() {
        return Objects.equals(getVersion(), getEffectiveVersion());
    }

    public boolean hasImplicitVersion() {
        return !Objects.equals(getVersion(), getEffectiveVersion());
    }

    /**
     * Uniqueness is used to avoid violations on coincidental artifact versions.
     *
     * <p>What about groupId's like this
     * - org.junit.jupiter
     * - org.junit.platform
     * Could remove one level of the package.
     *
     * <p>The coincidence problem puts the whole rule into jeopardy.
     * Without a good solution, can only report warnings.
     *
     * <p>How is each violation affected:
     * - redundantPropertyViolation
     * grouping by groupId ruins this - property could be used for multiple artifacts with the different groupIds.
     * - unusedPropertyViolation
     * grouping by groupId is ok - some locations should not use the property
     * - missingPropertyViolation
     * grouping by groupId is ok - some locations should not use the property
     *
     */
    public String uniqueness() {
        return String.join(":", getGroupId(), getEffectiveVersion());
    }
}

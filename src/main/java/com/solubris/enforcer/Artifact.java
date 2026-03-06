package com.solubris.enforcer;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.With;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.ReportPlugin;

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
}

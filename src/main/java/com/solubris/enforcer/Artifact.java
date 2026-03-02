package com.solubris.enforcer;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.ReportPlugin;

public class Artifact {
    private final String version;
    private final String artifactId;
    private final String groupId;
    private final String type;      // dependency, plugin, parent, etc.
    private final boolean managed;  // whether the version is from dependencyManagement or pluginManagement
    private final String profile;

    public Artifact(String version, String artifactId, String groupId, String type, boolean managed, String profile) {
        this.version = version;
        this.artifactId = artifactId;
        this.groupId = groupId;
        this.type = type;
        this.managed = managed;
        this.profile = profile;
    }

    public Artifact(Dependency dependency, boolean managed, String profile) {
        this(dependency.getVersion(), dependency.getArtifactId(), dependency.getGroupId(), "Dependency", managed, profile);
    }

    public Artifact(Plugin plugin, boolean managed, String profile) {
        this(plugin.getVersion(), plugin.getArtifactId(), plugin.getGroupId(), "Plugin", managed, profile);
    }

    public Artifact(ReportPlugin plugin) {
        this(plugin.getVersion(), plugin.getArtifactId(), plugin.getGroupId(), "ReportPlugin", false, null);
    }

    public Artifact(ReportPlugin plugin, String profile) {
        this(plugin.getVersion(), plugin.getArtifactId(), plugin.getGroupId(), "ReportPlugin", false, profile);
    }

    public Artifact(Extension extension) {
        this(extension.getVersion(), extension.getArtifactId(), extension.getGroupId(), "Extension", false, null);
    }

    public String getVersion() {
        return version;
    }

    public String getType() {
        return type;
    }

    public boolean isManaged() {
        return managed;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getGroupId() {
        return groupId;
    }

    @Override
    public String toString() {
        String profilePart = profile != null ? String.format(" (profile: %s)", profile) : "";
        return String.format("%s: %s:%s(%s)%s", type, groupId, artifactId, managed ? "managed" : "direct", profilePart);
    }

    public static Artifact direct(Dependency dependency) {
        return new Artifact(dependency, false, null);
    }

    public static Artifact managed(Dependency dependency) {
        return new Artifact(dependency, false, null);
    }
}

package com.solubris.enforcer;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Plugin;

public class Artifact {
    private final String version;
    private final String artifactId;
    private final String groupId;
    private final String type;      // dependency, plugin, parent, etc.
    private final boolean managed;  // whether the version is from dependencyManagement or pluginManagement

    public Artifact(String version, String artifactId, String groupId, String type) {
        this.version = version;
        this.artifactId = artifactId;
        this.groupId = groupId;
        this.type = type;
        this.managed = false;
    }

    public Artifact(Dependency dependency, String type) {
        this(dependency.getVersion(), dependency.getArtifactId(), dependency.getGroupId(), type);
    }

    public Artifact(Plugin plugin, String type) {
        this(plugin.getVersion(), plugin.getArtifactId(), plugin.getGroupId(), type);
    }

    public Artifact(Extension extension, String type) {
        this(extension.getVersion(), extension.getArtifactId(), extension.getGroupId(), type);
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
        return String.format("%s: %s:%s", type, groupId, artifactId);
    }
}

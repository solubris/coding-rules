package com.solubris.enforcer;

import org.apache.maven.model.Dependency;

public class ModelStubber {
    public static Dependency dependencyOf(String groupId, String artifactId, String version) {
        Dependency dep = new Dependency();
        dep.setGroupId(groupId);
        dep.setArtifactId(artifactId);
        dep.setVersion(version);
        return dep;
    }
}

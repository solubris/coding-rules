package com.solubris.enforcer;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.Reporting;

import java.util.Random;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.solubris.enforcer.PropertyUtil.asPlaceHolder;

public class ModelStubber {
    private static final RandomGenerator random = new Random();

    private final Model originalModel;
    private final Model effectiveModel;

    public ModelStubber(Model originalModel, Model effectiveModel) {
        this.originalModel = originalModel;
        this.effectiveModel = effectiveModel;
    }

    public static Dependency dependencyOf(String groupId, String artifactId, String version) {
        Dependency result = new Dependency();
        result.setGroupId(groupId);
        result.setArtifactId(artifactId);
        result.setVersion(version);
        return result;
    }

    public static Plugin pluginOf(String groupId, String artifactId, String version) {
        Plugin result = new Plugin();
        result.setGroupId(groupId);
        result.setArtifactId(artifactId);
        result.setVersion(version);
        return result;
    }

    public static Extension extensionOf(String groupId, String artifactId, String version) {
        Extension result = new Extension();
        result.setGroupId(groupId);
        result.setArtifactId(artifactId);
        result.setVersion(version);
        return result;
    }

    public static ReportPlugin reportPluginOf(String groupId, String artifactId, String version) {
        ReportPlugin result = new ReportPlugin();
        result.setGroupId(groupId);
        result.setArtifactId(artifactId);
        result.setVersion(version);
        return result;
    }

    public void withAllTypes(Supplier<String> versionProvider) {
        withDependency("junit", "junit", versionProvider.get());
        withManagedDependency("org.junit.jupiter", "jupiter-api", versionProvider.get());

        Plugin plugin = pluginOf("org.apache.maven.plugins", "maven-compiler-plugin", versionProvider.get());
        plugin.addDependency(dependencyOf("org.apache.maven", "maven-plugin-api", versionProvider.get()));
        Build build = new Build();
        build.addPlugin(plugin);
        build.addExtension(extensionOf("org.apache.maven", "maven-core-extension", versionProvider.get()));
        PluginManagement pluginManagement = new PluginManagement();
        pluginManagement.addPlugin(pluginOf("org.apache.maven.plugins", "surefire-plugin", versionProvider.get()));
        build.setPluginManagement(pluginManagement);
        Reporting reporting = new Reporting();
        reporting.addPlugin(reportPluginOf("org.apache.maven.plugins", "maven-site-plugin", versionProvider.get()));
        originalModel.setReporting(reporting);
        originalModel.setBuild(build);
        effectiveModel.setReporting(reporting);
        effectiveModel.setBuild(build);
    }

    public static String randomVersion() {
        return Stream.generate(() -> String.valueOf(random.nextInt(10)))
                .limit(3)
                .collect(Collectors.joining("."));
    }

    @CanIgnoreReturnValue
    private ModelStubber withManagedDependency(String groupId, String artifactId, String version) {
        DependencyManagement dependencyManagement = new DependencyManagement();
        dependencyManagement.addDependency(dependencyOf(groupId, artifactId, version));
        originalModel.setDependencyManagement(dependencyManagement);
        effectiveModel.setDependencyManagement(dependencyManagement);

        return this;
    }

    @CanIgnoreReturnValue
    public ModelStubber withDependency(String groupId, String artifactId, String version, String effectiveVersion) {
        effectiveModel.addDependency(dependencyOf(groupId, artifactId, effectiveVersion));
        originalModel.addProperty(version, effectiveVersion);
        version = asPlaceHolder(version);
        originalModel.addDependency(dependencyOf(groupId, artifactId, version));
        return this;
    }

    @CanIgnoreReturnValue
    public ModelStubber withDependency(String groupId, String artifactId, String version) {
        effectiveModel.addDependency(dependencyOf(groupId, artifactId, version));
        originalModel.addDependency(dependencyOf(groupId, artifactId, version));
        return this;
    }
}

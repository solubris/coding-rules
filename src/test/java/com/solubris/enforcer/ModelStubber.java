package com.solubris.enforcer;

import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.Reporting;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.solubris.NullSugar.coalesce;
import static com.solubris.enforcer.PropertyUtil.asPlaceHolder;

public class ModelStubber {
    private static final Set<String> previouslyGeneratedVersions = new HashSet<>();
    private static final RandomGenerator random = new Random();

    private final Model originalModel;
    private final Model effectiveModel;

    public ModelStubber(Model originalModel, Model effectiveModel) {
        this.originalModel = originalModel;
        this.effectiveModel = effectiveModel;
    }

    private static Build buildFrom(Model model) {
        return coalesce(model.getBuild(), new Build());
    }

    private static Reporting reportingFrom(Model model) {
        return coalesce(model.getReporting(), new Reporting());
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
        originalModel.setBuild(build);
        effectiveModel.setBuild(build);

        Reporting reporting = new Reporting();
        reporting.addPlugin(reportPluginOf("org.apache.maven.plugins", "maven-site-plugin", versionProvider.get()));
        originalModel.setReporting(reporting);
        effectiveModel.setReporting(reporting);
    }

    /**
     * Generates a random version string that has not been generated before, to avoid false positives in tests.
     */
    public static String randomVersion() {
        String result;

        do {
            result = Stream.generate(() -> String.valueOf(random.nextInt(10)))
                    .limit(3)
                    .collect(Collectors.joining("."));
        } while (!previouslyGeneratedVersions.add(result));

        return result;
    }

    public void withManagedDependency(String groupId, String artifactId, String version, String effectiveVersion) {
        DependencyManagement dependencyManagement = new DependencyManagement();
        dependencyManagement.addDependency(dependencyOf(groupId, artifactId, effectiveVersion));
        effectiveModel.setDependencyManagement(dependencyManagement);

        originalModel.addProperty(version, effectiveVersion);
        dependencyManagement = new DependencyManagement();
        dependencyManagement.addDependency(dependencyOf(groupId, artifactId, asPlaceHolder(version)));
        originalModel.setDependencyManagement(dependencyManagement);
    }

    public void withManagedDependency(String groupId, String artifactId, String version) {
        DependencyManagement dependencyManagement = new DependencyManagement();
        dependencyManagement.addDependency(dependencyOf(groupId, artifactId, version));
        effectiveModel.setDependencyManagement(dependencyManagement);
        originalModel.setDependencyManagement(dependencyManagement);
    }

    public void withDependency(String groupId, String artifactId, String version, String effectiveVersion) {
        effectiveModel.addDependency(dependencyOf(groupId, artifactId, effectiveVersion));
        originalModel.addProperty(version, effectiveVersion);
        originalModel.addDependency(dependencyOf(groupId, artifactId, asPlaceHolder(version)));
    }

    public void withDependency(String groupId, String artifactId, String version) {
        effectiveModel.addDependency(dependencyOf(groupId, artifactId, version));
        originalModel.addDependency(dependencyOf(groupId, artifactId, version));
    }

    public void withPlugin(String groupId, String artifactId, String version, String effectiveVersion) {
        Build build = buildFrom(effectiveModel);
        build.addPlugin(pluginOf(groupId, artifactId, effectiveVersion));
        effectiveModel.setBuild(build);

        build = buildFrom(originalModel);
        build.addPlugin(pluginOf(groupId, artifactId, asPlaceHolder(version)));
        originalModel.setBuild(build);
        originalModel.addProperty(version, effectiveVersion);
    }

    public void withPlugin(String groupId, String artifactId, String version) {
        Build build = buildFrom(effectiveModel);
        build.addPlugin(pluginOf(groupId, artifactId, version));
        effectiveModel.setBuild(build);
        build = buildFrom(originalModel);
        build.addPlugin(pluginOf(groupId, artifactId, version));
        originalModel.setBuild(build);
    }

    public void withManagedPlugin(String groupId, String artifactId, String version, String effectiveVersion) {
        PluginManagement pluginManagement = new PluginManagement();
        pluginManagement.addPlugin(pluginOf(groupId, artifactId, effectiveVersion));
        Build build = buildFrom(effectiveModel);
        build.setPluginManagement(pluginManagement);
        effectiveModel.setBuild(build);

        pluginManagement = new PluginManagement();
        pluginManagement.addPlugin(pluginOf(groupId, artifactId, asPlaceHolder(version)));
        build = buildFrom(originalModel);
        build.setPluginManagement(pluginManagement);
        originalModel.setBuild(build);
        originalModel.addProperty(version, effectiveVersion);
    }

    public void withManagedPlugin(String groupId, String artifactId, String version) {
        PluginManagement pluginManagement = new PluginManagement();
        pluginManagement.addPlugin(pluginOf(groupId, artifactId, version));
        Build build = buildFrom(effectiveModel);
        build.setPluginManagement(pluginManagement);
        effectiveModel.setBuild(build);

        pluginManagement = new PluginManagement();
        pluginManagement.addPlugin(pluginOf(groupId, artifactId, version));
        build = buildFrom(originalModel);
        build.setPluginManagement(pluginManagement);
        originalModel.setBuild(build);
    }

    public void withExtension(String groupId, String artifactId, String version, String effectiveVersion) {
        Build build = buildFrom(effectiveModel);
        build.addExtension(extensionOf(groupId, artifactId, effectiveVersion));
        effectiveModel.setBuild(build);

        build = buildFrom(originalModel);
        build.addExtension(extensionOf(groupId, artifactId, asPlaceHolder(version)));
        originalModel.setBuild(build);
        originalModel.addProperty(version, effectiveVersion);
    }

    public void withExtension(String groupId, String artifactId, String version) {
        Build build = buildFrom(effectiveModel);
        build.addExtension(extensionOf(groupId, artifactId, version));
        effectiveModel.setBuild(build);
        build = buildFrom(originalModel);
        build.addExtension(extensionOf(groupId, artifactId, version));
        originalModel.setBuild(build);
    }

    public void withReportPlugin(String groupId, String artifactId, String version, String effectiveVersion) {
        Reporting reporting = reportingFrom(effectiveModel);
        reporting.addPlugin(reportPluginOf(groupId, artifactId, effectiveVersion));
        effectiveModel.setReporting(reporting);

        reporting = reportingFrom(originalModel);
        reporting.addPlugin(reportPluginOf(groupId, artifactId, asPlaceHolder(version)));
        originalModel.setReporting(reporting);
        originalModel.addProperty(version, effectiveVersion);
    }

    public void withReportPlugin(String groupId, String artifactId, String version) {
        Reporting reporting = reportingFrom(effectiveModel);
        reporting.addPlugin(reportPluginOf(groupId, artifactId, version));
        effectiveModel.setReporting(reporting);
        reporting = reportingFrom(originalModel);
        reporting.addPlugin(reportPluginOf(groupId, artifactId, version));
        originalModel.setReporting(reporting);
    }
}

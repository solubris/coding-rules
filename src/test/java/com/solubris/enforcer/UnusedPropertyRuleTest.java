package com.solubris.enforcer;

import org.apache.maven.enforcer.rule.api.EnforcerLogger;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.model.Build;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.Reporting;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static com.solubris.enforcer.ModelStubber.dependencyOf;
import static com.solubris.enforcer.ModelStubber.extensionOf;
import static com.solubris.enforcer.ModelStubber.pluginOf;
import static com.solubris.enforcer.ModelStubber.reportPluginOf;
import static com.solubris.enforcer.UnusedPropertyRule.SUPPRESSIONS_PROPERTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class UnusedPropertyRuleTest {
    private final Model originalModel = new Model();
    private final Model effectiveModel = new Model();
    private final UnusedPropertyRule rule;

    UnusedPropertyRuleTest() {
        UnusedPropertyRule rule = new UnusedPropertyRule(originalModel, effectiveModel);
        rule.setLog(mock(EnforcerLogger.class));
        this.rule = rule;
    }

    @Test
    void noPropertiesPasses() {
        assertThatNoException().isThrownBy(rule::execute);
    }

    @Test
    void versionPropertyUsedByDirectDependencyPasses() {
        originalModel.addProperty("junit.version", "5.9.3");
        originalModel.addDependency(dependencyOf("org.junit", "junit", "${junit.version}"));

        effectiveModel.addDependency(dependencyOf("org.junit", "junit", "5.9.3"));

        Stream<String> violations = rule.scanProperties();

        assertThat(violations).isEmpty();
    }

    @Test
    void versionPropertyUsedByManagedDependencyPasses() {
        originalModel.addProperty("junit.version", "5.9.3");
        DependencyManagement originalDepMgmt = new DependencyManagement();
        originalDepMgmt.addDependency(dependencyOf("org.junit", "junit", "${junit.version}"));
        originalModel.setDependencyManagement(originalDepMgmt);

        DependencyManagement effectiveDepMgmt = new DependencyManagement();
        effectiveDepMgmt.addDependency(dependencyOf("org.junit", "junit", "5.9.3"));
        effectiveModel.setDependencyManagement(effectiveDepMgmt);

        Stream<String> violations = rule.scanProperties();

        assertThat(violations).isEmpty();
    }

    @Test
    void versionPropertyUsedByPluginPasses() {
        originalModel.addProperty("compiler.version", "3.13.0");
        Build originalBuild = new Build();
        originalBuild.addPlugin(pluginOf("org.apache.maven.plugins", "maven-compiler-plugin", "${compiler.version}"));
        originalModel.setBuild(originalBuild);

        Build effectiveBuild = new Build();
        effectiveBuild.addPlugin(pluginOf("org.apache.maven.plugins", "maven-compiler-plugin", "3.13.0"));
        effectiveModel.setBuild(effectiveBuild);

        Stream<String> violations = rule.scanProperties();

        assertThat(violations).isEmpty();
    }

    @Test
    void versionPropertyUsedByManagedPluginPasses() {
        originalModel.addProperty("compiler.version", "3.13.0");
        Build originalBuild = new Build();
        PluginManagement originalPluginMgmt = new PluginManagement();
        originalPluginMgmt.addPlugin(pluginOf("org.apache.maven.plugins", "maven-compiler-plugin", "${compiler.version}"));
        originalBuild.setPluginManagement(originalPluginMgmt);
        originalModel.setBuild(originalBuild);

        Build effectiveBuild = new Build();
        PluginManagement effectivePluginMgmt = new PluginManagement();
        effectivePluginMgmt.addPlugin(pluginOf("org.apache.maven.plugins", "maven-compiler-plugin", "3.13.0"));
        effectiveBuild.setPluginManagement(effectivePluginMgmt);
        effectiveModel.setBuild(effectiveBuild);

        Stream<String> violations = rule.scanProperties();

        assertThat(violations).isEmpty();
    }

    @Test
    void versionPropertyUsedByExtensionPasses() {
        originalModel.addProperty("ext.version", "1.0.0");
        Build originalBuild = new Build();
        originalBuild.addExtension(extensionOf("org.example", "my-extension", "${ext.version}"));
        originalModel.setBuild(originalBuild);

        Build effectiveBuild = new Build();
        effectiveBuild.addExtension(extensionOf("org.example", "my-extension", "1.0.0"));
        effectiveModel.setBuild(effectiveBuild);

        Stream<String> violations = rule.scanProperties();

        assertThat(violations).isEmpty();
    }

    @Test
    void versionPropertyUsedByReportPluginPasses() {
        originalModel.addProperty("site.version", "4.0.0");
        Reporting originalReporting = new Reporting();
        originalReporting.addPlugin(reportPluginOf("org.apache.maven.plugins", "maven-site-plugin", "${site.version}"));
        originalModel.setReporting(originalReporting);

        Reporting effectiveReporting = new Reporting();
        effectiveReporting.addPlugin(reportPluginOf("org.apache.maven.plugins", "maven-site-plugin", "4.0.0"));
        effectiveModel.setReporting(effectiveReporting);

        Stream<String> violations = rule.scanProperties();

        assertThat(violations).isEmpty();
    }

    @Test
    void unusedVersionPropertyFails() {
        originalModel.addProperty("old-lib.version", "2.0.0");

        Stream<String> violations = rule.scanProperties();

        assertThat(violations)
                .hasSize(1)
                .first()
                .asString()
                .contains("old-lib.version")
                .contains("2.0.0")
                .contains("unused");
    }

    @Test
    void suppressedUnusedPropertyIsIgnored() {
        originalModel.addProperty(SUPPRESSIONS_PROPERTY, "old-lib.version, kept-for-compat.version");
        originalModel.addProperty("old-lib.version", "2.0.0");

        Stream<String> violations = rule.scanProperties();

        assertThat(violations).isEmpty();
    }

    @Test
    void nonVersionPropertyIsIgnored() {
        originalModel.addProperty("project.build.sourceEncoding", "UTF-8");

        Stream<String> violations = rule.scanProperties();

        assertThat(violations).isEmpty();
    }

    @Test
    void multiplePropertiesMixedUsage() {
        originalModel.addProperty("junit.version", "5.9.3");
        originalModel.addProperty("old-lib.version", "2.0.0");
        originalModel.addDependency(dependencyOf("org.junit", "junit", "${junit.version}"));

        effectiveModel.addDependency(dependencyOf("org.junit", "junit", "5.9.3"));

        Stream<String> violations = rule.scanProperties();

        assertThat(violations)
                .hasSize(1)
                .allMatch(v -> v.contains("old-lib.version"));
    }

    @Test
    void multipleUnusedPropertiesReported() {
        originalModel.addProperty("lib-a.version", "1.0.0");
        originalModel.addProperty("lib-b.version", "2.0.0");

        Stream<String> violations = rule.scanProperties();

        assertThat(violations)
                .hasSize(2)
                .anyMatch(v -> v.contains("lib-a.version"))
                .anyMatch(v -> v.contains("lib-b.version"));
    }

    @Test
    void executeThrowsOnUnusedProperty() {
        originalModel.addProperty("orphaned.version", "3.0.0");

        assertThatThrownBy(rule::execute)
                .isInstanceOf(EnforcerRuleException.class);
    }

    @Test
    void executePassesWhenAllPropertiesUsed() {
        originalModel.addProperty("junit.version", "5.9.3");
        originalModel.addDependency(dependencyOf("org.junit", "junit", "${junit.version}"));

        effectiveModel.addDependency(dependencyOf("org.junit", "junit", "5.9.3"));

        assertThatNoException().isThrownBy(rule::execute);
    }

    @Test
    void versionPropertyUsedByPluginDependencyPasses() {
        originalModel.addProperty("api.version", "3.0.0");
        Build originalBuild = new Build();
        Plugin plugin = pluginOf("org.apache.maven.plugins", "maven-compiler-plugin", "3.13.0");
        plugin.addDependency(dependencyOf("org.apache.maven", "maven-plugin-api", "${api.version}"));
        originalBuild.addPlugin(plugin);
        originalModel.setBuild(originalBuild);

        Build effectiveBuild = new Build();
        Plugin effectivePlugin = pluginOf("org.apache.maven.plugins", "maven-compiler-plugin", "3.13.0");
        effectivePlugin.addDependency(dependencyOf("org.apache.maven", "maven-plugin-api", "3.0.0"));
        effectiveBuild.addPlugin(effectivePlugin);
        effectiveModel.setBuild(effectiveBuild);

        Stream<String> violations = rule.scanProperties();

        assertThat(violations).isEmpty();
    }
}

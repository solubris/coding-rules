package com.solubris.enforcer;

import org.apache.maven.enforcer.rule.api.EnforcerLogger;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static com.solubris.enforcer.ModelStubber.dependencyOf;
import static com.solubris.enforcer.ModelStubber.pluginOf;
import static com.solubris.enforcer.UnusedPropertyRule.SUPPRESSIONS_PROPERTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class UnusedPropertyRuleTest {
    private final Model originalModel = new Model();
    private final Model effectiveModel = new Model();
    private final UnusedPropertyRule rule = new UnusedPropertyRule(originalModel, effectiveModel);
    private final ModelStubber stubber = new ModelStubber(originalModel, effectiveModel);

    UnusedPropertyRuleTest() {
        rule.setLog(mock(EnforcerLogger.class));
    }

    @Test
    void noPropertiesPasses() {
        assertThatNoException().isThrownBy(rule::execute);
    }

    @Test
    void versionPropertyUsedByDependencyPasses() {
        stubber.withDependency("org.junit", "junit", "junit.version", "4.13.2");
        stubber.withManagedDependency("org.mockito", "mockito-core", "mockito.version", "5.22.0");

        Stream<String> violations = rule.scan();

        assertThat(violations).isEmpty();
    }

    @Test
    void versionPropertyUsedByPluginPasses() {
        stubber.withPlugin("apache.maven.plugins", "maven-compiler-plugin", "compiler.version", "3.13.0");
        stubber.withManagedPlugin("org.jacoco", "jacoco-maven-plugin", "jacoco.version", "0.8.12");

        Stream<String> violations = rule.scan();

        assertThat(violations).isEmpty();
    }

    @Test
    void versionPropertyUsedByExtensionPasses() {
        stubber.withExtension("org.example", "my-extension", "ext.version", "1.0.0");

        Stream<String> violations = rule.scan();

        assertThat(violations).isEmpty();
    }

    @Test
    void versionPropertyUsedByReportPluginPasses() {
        stubber.withReportPlugin("apache.maven.plugins", "maven-site-plugin", "site.version", "4.0.0");

        Stream<String> violations = rule.scan();

        assertThat(violations).isEmpty();
    }

    @Test
    void unusedVersionPropertyFails() {
        originalModel.addProperty("old-lib.version", "2.0.0");

        Stream<String> violations = rule.scan();

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

        Stream<String> violations = rule.scan();

        assertThat(violations).isEmpty();
    }

    @Test
    void nonVersionPropertyIsIgnored() {
        originalModel.addProperty("project.build.sourceEncoding", "UTF-8");

        Stream<String> violations = rule.scan();

        assertThat(violations).isEmpty();
    }

    @Test
    void multiplePropertiesMixedUsage() {
        originalModel.addProperty("junit.version", "5.9.3");
        originalModel.addProperty("old-lib.version", "2.0.0");
        originalModel.addDependency(dependencyOf("org.junit", "junit", "${junit.version}"));

        effectiveModel.addDependency(dependencyOf("org.junit", "junit", "5.9.3"));

        Stream<String> violations = rule.scan();

        assertThat(violations)
                .hasSize(1)
                .allMatch(v -> v.contains("old-lib.version"));
    }

    @Test
    void multipleUnusedPropertiesReported() {
        originalModel.addProperty("lib-a.version", "1.0.0");
        originalModel.addProperty("lib-b.version", "2.0.0");

        Stream<String> violations = rule.scan();

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

        Stream<String> violations = rule.scan();

        assertThat(violations).isEmpty();
    }
}

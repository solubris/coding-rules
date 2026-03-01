package com.solubris.enforcer;

import org.apache.maven.enforcer.rule.api.EnforcerLogger;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static com.solubris.enforcer.ModelStubber.dependencyOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Test cases for VersionPropertyRule enforcer rule.
 */
class VersionPropertyRuleTest {
    private final Model model = new Model();
    private final VersionPropertyRule rule = new VersionPropertyRule(model);

    VersionPropertyRuleTest() {
        rule.setLog(mock(EnforcerLogger.class));
    }

    @Test
    public void testSingleVersionHardcoded() throws Exception {
        model.addDependency(dependencyOf("junit", "junit", "4.13.2"));

        Stream<String> violations = rule.scanAll();

        assertThat(violations).isEmpty();
    }

    @Test
    public void testSingleVersionWithProperty() throws Exception {
        model.addProperty("junit.version", "4.13.2");
        model.addDependency(dependencyOf("junit", "junit", "${junit.version}"));

        Stream<String> violations = rule.scanAll();

        assertThat(violations).hasSize(1);
    }

    @Test
    public void testMultipleVersionsWithProperty() throws Exception {
        model.addProperty("junit.version", "4.13.2");
        model.addDependency(dependencyOf("junit", "junit", "${junit.version}"));
        DependencyManagement depMgmt = new DependencyManagement();
        depMgmt.addDependency(dependencyOf("org.junit.jupiter", "junit-jupiter-api", "${junit.version}"));
        model.setDependencyManagement(depMgmt);

        Stream<String> violations = rule.scanAll();

        assertThat(violations).isEmpty();
    }

    @Test
    public void testMultipleVersionsWithoutProperty() throws Exception {
        rule.setRequirePropertiesForDuplicates(true);

        model.addDependency(dependencyOf("junit", "junit", "4.13.2"));
        DependencyManagement depMgmt = new DependencyManagement();
        depMgmt.addDependency(dependencyOf("org.junit.jupiter", "junit-jupiter-api", "4.13.2"));
        model.setDependencyManagement(depMgmt);

        Stream<String> violations = rule.scanAll();

        assertThat(violations).hasSize(1);
    }

    @Test
    public void testMultipleVersionsWithoutPropertyButDisabled() throws Exception {
        rule.setRequirePropertiesForDuplicates(false);

        model.addDependency(dependencyOf("junit", "junit", "4.13.2"));
        DependencyManagement depMgmt = new DependencyManagement();
        depMgmt.addDependency(dependencyOf("org.junit.jupiter", "junit-jupiter-api", "4.13.2"));
        model.setDependencyManagement(depMgmt);

        Stream<String> violations = rule.scanAll();

        assertThat(violations).isEmpty();
    }
}



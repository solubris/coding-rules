package com.solubris.enforcer;

import org.apache.maven.enforcer.rule.api.EnforcerLogger;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static com.solubris.enforcer.ModelStubber.dependencyOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class BomImportScopeRuleTest {
    private final Model model = new Model();
    private final BomImportScopeRule rule = new BomImportScopeRule(model);

    BomImportScopeRuleTest() {
        rule.setLog(mock(EnforcerLogger.class));
    }

    @Test
    void noDependenciesPasses() {
        assertThatNoException().isThrownBy(rule::execute);
    }

    @Test
    void jarDependencyPasses() {
        model.addDependency(dependencyOf("com.example", "some-lib", "1.0"));

        assertThatNoException().isThrownBy(rule::execute);
    }

    @Test
    void bomWithImportScopeInDepMgmtPasses() {
        Dependency bom = bomDependency("com.example", "some-bom", "1.0", "import");
        DependencyManagement depMgmt = new DependencyManagement();
        depMgmt.addDependency(bom);
        model.setDependencyManagement(depMgmt);

        assertThatNoException().isThrownBy(rule::execute);
    }

    @Test
    void bomWithoutImportScopeInDepMgmtFails() {
        Dependency bom = bomDependency("com.example", "some-bom", "1.0", "compile");
        DependencyManagement depMgmt = new DependencyManagement();
        depMgmt.addDependency(bom);
        model.setDependencyManagement(depMgmt);

        Stream<String> violations = rule.scanAll();

        assertThat(violations)
                .hasSize(1)
                .first()
                .asString()
                .contains("com.example:some-bom:1.0")
                .contains("scope=compile")
                .contains("dependencyManagement");
    }

    @Test
    void bomWithNoScopeInDepMgmtFails() {
        Dependency bom = bomDependency("com.example", "some-bom", "1.0", null);
        DependencyManagement depMgmt = new DependencyManagement();
        depMgmt.addDependency(bom);
        model.setDependencyManagement(depMgmt);

        Stream<String> violations = rule.scanAll();

        assertThat(violations)
                .hasSize(1)
                .first()
                .asString()
                .contains("com.example:some-bom:1.0")
                .contains("no scope");
    }

    @Test
    void bomInTopLevelDependenciesFails() {
        Dependency bom = bomDependency("com.example", "some-bom", "1.0", "import");
        model.addDependency(bom);

        Stream<String> violations = rule.scanAll();

        assertThat(violations)
                .hasSize(1)
                .first()
                .asString()
                .contains("com.example:some-bom:1.0")
                .contains("<dependencies>");
    }

    @Test
    void multipleViolationsReported() {
        // BOM in top-level dependencies
        Dependency topBom = bomDependency("com.example", "top-bom", "1.0", "compile");
        model.addDependency(topBom);

        // BOM in depMgmt without import scope
        Dependency mgmtBom = bomDependency("com.example", "mgmt-bom", "2.0", "runtime");
        DependencyManagement depMgmt = new DependencyManagement();
        depMgmt.addDependency(mgmtBom);
        model.setDependencyManagement(depMgmt);

        Stream<String> violations = rule.scanAll();

        assertThat(violations)
                .hasSize(2)
                .anyMatch(v -> v.contains("com.example:top-bom:1.0"))
                .anyMatch(v -> v.contains("com.example:mgmt-bom:2.0"));
    }

    @Test
    void mixedDependenciesWithValidBomPasses() {
        // Non-pom dependency in top-level deps
        model.addDependency(dependencyOf("com.example", "some-lib", "1.0"));

        // Valid BOM in depMgmt
        Dependency bom = bomDependency("com.example", "some-bom", "1.0", "import");
        DependencyManagement depMgmt = new DependencyManagement();
        depMgmt.addDependency(bom);
        model.setDependencyManagement(depMgmt);

        assertThatNoException().isThrownBy(rule::execute);
    }

    @Test
    void executeThrowsWithViolationMessage() {
        Dependency bom = bomDependency("com.example", "bad-bom", "3.0", "compile");
        DependencyManagement depMgmt = new DependencyManagement();
        depMgmt.addDependency(bom);
        model.setDependencyManagement(depMgmt);

        assertThatThrownBy(rule::execute)
                .isInstanceOf(EnforcerRuleException.class)
                .hasMessageContaining("com.example:bad-bom:3.0")
                .hasMessageContaining("1 violation(s)");
    }

    private static Dependency bomDependency(String groupId, String artifactId, String version, String scope) {
        Dependency dep = dependencyOf(groupId, artifactId, version);
        dep.setType("pom");
        dep.setScope(scope);
        return dep;
    }
}

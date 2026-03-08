package com.solubris.enforcer;

import org.apache.maven.enforcer.rule.api.EnforcerLogger;
import org.apache.maven.model.Model;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class VersionPropertyRuleTest {
    private final Model originalModel = new Model();
    private final Model effectiveModel = new Model();
    private final VersionPropertyRule rule = new VersionPropertyRule(originalModel, effectiveModel);
    private final ModelStubber modelStubber = new ModelStubber(originalModel, effectiveModel);

    VersionPropertyRuleTest() {
        rule.setLog(mock(EnforcerLogger.class));
    }

    @Test
    void singleExplicitVersionsAllowedCoveringAllTypes() {
        modelStubber.withAllTypes(ModelStubber::randomVersion);

        Stream<String> violations = rule.scan();

        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void detectsSingleUseOfProperty(boolean allowed) {
        rule.allowSingleUseOfProperty = allowed;
        modelStubber.withDependency("junit", "junit", "junit.version", "4.13.2");

        Stream<String> violations = rule.scan();

        assertThat(violations).hasSize(allowed ? 0 : 1);
    }

    @Test
    void multipleUseOfPropertyAllowed() {
        modelStubber.withDependency("org.junit.jupiter", "junit-engine", "junit.version", "4.13.2");
        modelStubber.withDependency("org.junit.jupiter", "junit-jupiter-api", "junit.version", "4.13.2");

        Stream<String> violations = rule.scan();

        assertThat(violations).isEmpty();
    }

    /**
     * XXX This detection may produce false positives if the same version string is used in multiple places,
     * but these occurrences are dependencies coincidentally using the same version.
     * How to solve this?
     * Only have the groupId:artifactId to go.
     * If the groupId matches, then we can be reasonably sure it's a duplicate.
     */
    @Nested
    class RequirePropertiesForDuplicates {
        @Test
        void multipleExplicitVersionsNotAllowed() {
            modelStubber.withDependency("org.junit.jupiter", "junit-engine", "4.13.2");
            modelStubber.withDependency("org.junit.jupiter", "junit-jupiter-api", "4.13.2");

//            DependencyManagement depMgmt = new DependencyManagement();
//            depMgmt.addDependency(dependencyOf("org.junit.jupiter", "junit-jupiter-api", "4.13.2"));
//            originalModel.setDependencyManagement(depMgmt);

            Stream<String> violations = rule.scan();

            assertThat(violations).hasSize(1);
        }

        /**
         * Entirely legitimate to use the same version for disparate dependencies.
         * Assume they must have a different groupId.
         */
        @Test
        void coincidentalVersionsAllowed() {
            modelStubber.withDependency("com.pmd", "pmd.core", "4.13.2");
            modelStubber.withDependency("org.junit.jupiter", "junit-jupiter-api", "4.13.2");

            Stream<String> violations = rule.scan();

            assertThat(violations).isEmpty();
        }

        @Test
        void multipleExplicitVersionsNotAllowedCoveringAllTypes() {
            modelStubber.withAllTypes(() -> "4.13.2");

            Stream<String> violations = rule.scan();

            // TODO how to assert the multiple locations for this violation?
            assertThat(violations).isNotEmpty();
        }

        @Test
        void multipleExplicitVersionsAllowedWhenDisabled() {
            rule.requirePropertiesForDuplicates = false;

            modelStubber.withDependency("junit", "junit", "4.13.2");
            modelStubber.withDependency("org.junit.jupiter", "junit-jupiter-api", "4.13.2");

            Stream<String> violations = rule.scan();

            assertThat(violations).isEmpty();
        }
    }
}

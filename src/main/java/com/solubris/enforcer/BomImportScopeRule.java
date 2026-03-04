package com.solubris.enforcer;

import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Stream;

import static java.text.MessageFormat.format;
import static java.util.stream.Collectors.joining;
import static org.apache.maven.artifact.ArtifactUtils.key;

/**
 * A custom Maven Enforcer rule that ensures all dependencies declared with
 * {@code type=pom} (i.e. BOMs) also specify {@code scope=import}.
 *
 * <p>This rule inspects both the top-level {@code <dependencies>} section and
 * the {@code <dependencyManagement>} section of the project. Any dependency
 * with {@code type=pom} that does not have {@code scope=import} will cause
 * a build failure.
 *
 * <p>BOMs (Bill of Materials) are intended to be imported via
 * {@code <scope>import</scope>} inside {@code <dependencyManagement>}.
 * Using them without import scope is almost always a mistake and can lead
 * to unexpected transitive dependency resolution.
 */
@Named("bomImportScopeRule")
public class BomImportScopeRule extends AbstractEnforcerRule {
    private static final String POM_TYPE = "pom";
    private static final String IMPORT_SCOPE = "import";

    private final Model model;

    @SuppressWarnings("unused")
    @Inject
    public BomImportScopeRule(MavenSession session) {
        this(modelFrom(session));
    }

    protected BomImportScopeRule(Model model) {
        this.model = model;
    }

    private static Model modelFrom(MavenSession session) {
        MavenProject project = session.getCurrentProject();
        Model originalModel = project.getOriginalModel();
        return originalModel != null ? originalModel : project.getModel();
    }

    @Override
    public void execute() throws EnforcerRuleException {
        LongAdder count = new LongAdder();
        String body = scanAll()
                .peek(v -> count.increment())
                .map(v -> "  - " + v)
                .collect(joining("\n", "", "\n"));
        if (count.longValue() > 0) {
            String title = "BOM dependencies (type=pom) must use scope=import. Found " + count.sum() + " violation(s):";
            throw new EnforcerRuleException(title + "\n" + body);
        }

        getLog().info("All BOM dependencies (type=pom) correctly use scope=import.");
    }

    protected Stream<String> scanAll() {
        Stream<String> topLevelViolations = directDependencies()
                .filter(dep -> POM_TYPE.equals(dep.getType()))
                .map(dep -> formatViolation(dep, "dependencies"));

        Stream<String> depMgmtViolations = managedDependencies()
                .filter(dep -> POM_TYPE.equals(dep.getType()))
                .filter(dep -> !IMPORT_SCOPE.equals(dep.getScope()))
                .map(dep -> formatViolation(dep, "dependencyManagement"));

        return Stream.concat(topLevelViolations, depMgmtViolations);
    }

    private Stream<Dependency> directDependencies() {
        return Optional.ofNullable(model.getDependencies())
                .stream()
                .flatMap(Collection::stream);
    }

    private Stream<Dependency> managedDependencies() {
        return Optional.ofNullable(model.getDependencyManagement())
                .map(DependencyManagement::getDependencies)
                .stream()
                .flatMap(Collection::stream);
    }

    private static String formatViolation(Dependency dep, String section) {
        String scope = dep.getScope();
        String scopeInfo = (scope == null || scope.isEmpty()) ? "no scope" : "scope=" + scope;
        String key = key(dep.getGroupId(), dep.getArtifactId(), dep.getVersion());
        return format("{0} in <{1}> has type=pom but {2} (expected scope=import)", key, section, scopeInfo);
    }
}

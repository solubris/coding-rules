package com.solubris.enforcer;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.BuildBase;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.ModelBase;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Reporting;
import org.apache.maven.project.MavenProject;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

import static com.solubris.StreamSugar.prepend;
import static java.util.function.Function.identity;

public class ModelScanner {
    private ModelScanner() {
    }

    static Stream<Artifact> scanModel(Model model) {
        Stream.Builder<Stream<Artifact>> result = Stream.builder();
        result.add(directDependencies(model));
        result.add(managedDependencies(model));
        result.add(scanPlugins(directPlugins(model), false, null));
        result.add(scanPlugins(managedPlugins(model), true, null));
        result.add(reportPlugins(model));
        result.add(extensions(model));
        result.add(scanProfiles(model));
        return result.build()
                .flatMap(identity());
    }

    private static Stream<Artifact> directDependencies(ModelBase model) {
        return Optional.ofNullable(model.getDependencies())
                .stream()
                .flatMap(Collection::stream)
                .map(Artifact::direct);
    }

    private static Stream<Artifact> managedDependencies(ModelBase model) {
        return Optional.ofNullable(model.getDependencyManagement())
                .map(DependencyManagement::getDependencies)
                .stream()
                .flatMap(Collection::stream)
                .map(Artifact::managed);
    }

    private static Stream<Artifact> extensions(Model model) {
        return Optional.ofNullable(model.getBuild())
                .map(Build::getExtensions)
                .stream()
                .flatMap(Collection::stream)
                .map(Artifact::new);
    }

    private static Stream<Artifact> reportPlugins(ModelBase model) {
        return Optional.ofNullable(model.getReporting())
                .map(Reporting::getPlugins)
                .stream()
                .flatMap(Collection::stream)
                .map(Artifact::new);
    }

    private static Stream<Plugin> managedPlugins(Model model) {
        return Optional.ofNullable(model.getBuild())
                .map(BuildBase::getPluginManagement)
                .map(PluginManagement::getPlugins)
                .stream()
                .flatMap(Collection::stream);
    }

    private static Stream<Plugin> managedPlugins(Profile model) {
        return Optional.ofNullable(model.getBuild())
                .map(BuildBase::getPluginManagement)
                .map(PluginManagement::getPlugins)
                .stream()
                .flatMap(Collection::stream);
    }

    private static Stream<Plugin> directPlugins(Model model) {
        return Optional.ofNullable(model.getBuild())
                .map(BuildBase::getPlugins)
                .stream()
                .flatMap(Collection::stream);
    }

    private static Stream<Plugin> directPlugins(Profile profile) {
        return Optional.ofNullable(profile.getBuild())
                .map(BuildBase::getPlugins)
                .stream()
                .flatMap(Collection::stream);
    }

    private static Stream<Dependency> pluginDependencies(Plugin plugin) {
        return Optional.ofNullable(plugin.getDependencies())
                .stream()
                .flatMap(Collection::stream);
    }

    private static Stream<Artifact> scanProfiles(Model model) {
        if (model.getProfiles() == null) return Stream.empty();

        return model.getProfiles().stream()
                .flatMap(profile -> {
                    // TODO what if the property is in the profile?
                    String profileId = profile.getId();
                    Stream.Builder<Stream<Artifact>> result = Stream.builder();
                    result.add(directDependencies(profile));
                    result.add(managedDependencies(profile));
                    result.add(scanPlugins(directPlugins(profile), false, profileId));
                    result.add(scanPlugins(managedPlugins(profile), true, profileId));
                    result.add(reportPlugins(profile));
                    return result.build()
                            .flatMap(identity())
                            .map(a -> a.withProfile(profileId));
                });
    }

    /**
     * TODO Should plugin dependencies show as a different type than regular dependencies?
     * TODO Consider whether plugin dependencies should be considered "managed" when the plugin is managed.
     */
    private static Stream<Artifact> scanPlugins(Stream<Plugin> items, boolean managed, String profile) {
        return items.flatMap(p ->
                prepend(new Artifact(p, managed, profile), pluginDependencies(p).map(d -> new Artifact(d, managed, profile)))
        );
    }

    static Model modelFrom(MavenSession session) {
        // Use original model to avoid implicit/default plugins and dependencies
        // that Maven adds automatically (e.g., default compiler plugin)
        MavenProject project = session.getCurrentProject();
        Model originalModel = project.getOriginalModel();
        return originalModel != null ? originalModel : project.getModel();
    }
}

package com.solubris.enforcer;

import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;

import java.util.Random;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ModelStubber {
    private static final RandomGenerator random = new Random();

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

    public static void withAllTypes(Model model, Supplier<String> versionProvider) {
        model.addDependency(dependencyOf("junit", "junit", versionProvider.get()));
        DependencyManagement dependencyManagement = new DependencyManagement();
        dependencyManagement.addDependency(dependencyOf("org.junit.jupiter", "junit-jupiter-api", versionProvider.get()));
        model.setDependencyManagement(dependencyManagement);
        Plugin plugin = pluginOf("org.apache.maven.plugins", "maven-compiler-plugin", versionProvider.get());
        plugin.addDependency(dependencyOf("org.apache.maven", "maven-plugin-api", versionProvider.get()));
        Build build = new Build();
        build.addPlugin(plugin);
        build.addExtension(extensionOf("org.apache.maven", "maven-core-extension", versionProvider.get()));
        PluginManagement pluginManagement = new PluginManagement();
        pluginManagement.addPlugin(pluginOf("org.apache.maven.plugins", "maven-surefire-plugin", versionProvider.get()));
        build.setPluginManagement(pluginManagement);
        model.setBuild(build);
    }

    public static String randomVersion() {
        return Stream.generate(() -> String.valueOf(random.nextInt(10)))
                .limit(3)
                .collect(Collectors.joining("."));
    }
}

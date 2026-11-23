package io.github.parkiyong.jaybee.api;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.time.Instant;
import java.util.zip.ZipInputStream;

/**
 * Fluent builder for assembling and deploying process resources.
 */
public interface DeploymentBuilder {

    DeploymentBuilder named(String name);

    DeploymentBuilder deployedAt(Instant timestamp);

    DeploymentBuilder addStringResource(String resourceName, String utf8Content);

    DeploymentBuilder addClasspathResource(String resourcePath);

    DeploymentBuilder addStreamResource(String resourceName, InputStream inputStream);

    DeploymentBuilder addFileResource(Path filePath);

    DeploymentBuilder addUrlResource(URL url);

    DeploymentBuilder addZipArchive(ZipInputStream zipStream);

    String deploy();
}

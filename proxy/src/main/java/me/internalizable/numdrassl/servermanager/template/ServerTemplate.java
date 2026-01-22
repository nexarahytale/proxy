package me.internalizable.numdrassl.servermanager.template;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a server template on disk.
 *
 * <p>Templates are stored in {@code servers/templates/<name>/} and contain
 * all files needed to spawn a dynamic server instance.</p>
 *
 * <h2>Required Structure</h2>
 * <pre>
 * servers/templates/bedwars/
 * ├── template.yml          # Template metadata (required)
 * ├── server.properties     # Hytale server config (required)
 * ├── startup.sh            # Startup script (optional)
 * ├── plugins/              # Plugin directory (optional)
 * │   └── NumdrasslBridge.jar
 * └── world/                # World data (optional, if not reset-on-spawn)
 * </pre>
 */
public class ServerTemplate {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerTemplate.class);

    private static final String TEMPLATE_METADATA_FILE = "template.yml";
    private static final String SERVER_PROPERTIES_FILE = "server.properties";
    private static final String STARTUP_SCRIPT = "startup.sh";
    private static final String PLUGINS_DIR = "plugins";
    private static final String WORLD_DIR = "world";

    private final String name;
    private final Path templatePath;
    private TemplateMetadata metadata;
    private List<String> validationErrors = new ArrayList<>();

    /**
     * Create a template reference.
     *
     * @param name template name (directory name)
     * @param templatePath path to template directory
     */
    public ServerTemplate(@Nonnull String name, @Nonnull Path templatePath) {
        this.name = Objects.requireNonNull(name, "name");
        this.templatePath = Objects.requireNonNull(templatePath, "templatePath");
    }

    /**
     * Load template from disk.
     *
     * @param templatePath path to template directory
     * @return loaded template
     * @throws IOException if loading fails
     */
    @Nonnull
    public static ServerTemplate loadFromDisk(@Nonnull Path templatePath) throws IOException {
        String name = templatePath.getFileName().toString();
        ServerTemplate template = new ServerTemplate(name, templatePath);
        template.load();
        return template;
    }

    /**
     * Load template metadata from disk.
     *
     * @throws IOException if loading fails
     */
    public void load() throws IOException {
        Path metadataPath = templatePath.resolve(TEMPLATE_METADATA_FILE);
        if (Files.exists(metadataPath)) {
            this.metadata = TemplateMetadata.load(metadataPath);
            if (metadata.getName() == null) {
                metadata.setName(name);
            }
        } else {
            // Create default metadata
            this.metadata = new TemplateMetadata();
            metadata.setName(name);
            metadata.setServerIdPrefix(name.toLowerCase());
        }
    }

    /**
     * Validate the template structure.
     *
     * @return true if template is valid
     */
    public boolean validate() {
        validationErrors.clear();

        if (!Files.isDirectory(templatePath)) {
            validationErrors.add("Template directory does not exist: " + templatePath);
            return false;
        }

        // Check for server.properties (required for Hytale)
        Path serverProps = templatePath.resolve(SERVER_PROPERTIES_FILE);
        if (!Files.exists(serverProps)) {
            validationErrors.add("Missing required file: " + SERVER_PROPERTIES_FILE);
        }

        // Check for template.yml (recommended)
        Path templateYml = templatePath.resolve(TEMPLATE_METADATA_FILE);
        if (!Files.exists(templateYml)) {
            LOGGER.warn("Template '{}' missing template.yml, using defaults", name);
        }

        // Check for startup script or server jar
        Path startupScript = templatePath.resolve(STARTUP_SCRIPT);
        boolean hasStartup = Files.exists(startupScript);
        boolean hasServerJar = metadata != null && metadata.getServerJar() != null
                && Files.exists(templatePath.resolve(metadata.getServerJar()));

        if (!hasStartup && !hasServerJar) {
            // Look for any .jar file
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(templatePath, "*.jar")) {
                boolean foundJar = stream.iterator().hasNext();
                if (!foundJar) {
                    validationErrors.add("No startup.sh or server JAR found");
                }
            } catch (IOException e) {
                validationErrors.add("Failed to scan for JAR files: " + e.getMessage());
            }
        }

        // Check plugins directory for Bridge
        Path pluginsDir = templatePath.resolve(PLUGINS_DIR);
        if (Files.isDirectory(pluginsDir)) {
            Path bridgeJar = pluginsDir.resolve("NumdrasslBridge.jar");
            if (!Files.exists(bridgeJar)) {
                LOGGER.warn("Template '{}' missing NumdrasslBridge.jar in plugins/", name);
            }
        }

        return validationErrors.isEmpty();
    }

    /**
     * Clone this template to a destination directory.
     *
     * @param destination target directory
     * @throws IOException if cloning fails
     */
    public void cloneTo(@Nonnull Path destination) throws IOException {
        cloneTo(destination, null);
    }

    /**
     * Clone this template to a destination directory with config overrides.
     *
     * @param destination target directory
     * @param configOverrides properties to override in server.properties
     * @throws IOException if cloning fails
     */
    public void cloneTo(@Nonnull Path destination, @Nullable java.util.Properties configOverrides) throws IOException {
        Objects.requireNonNull(destination, "destination");

        if (Files.exists(destination)) {
            throw new IOException("Destination already exists: " + destination);
        }

        LOGGER.debug("Cloning template '{}' to {}", name, destination);

        // Copy entire template directory
        Files.walkFileTree(templatePath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = destination.resolve(templatePath.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = destination.resolve(templatePath.relativize(file));
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });

        // Apply config overrides to server.properties
        if (configOverrides != null && !configOverrides.isEmpty()) {
            Path propsPath = destination.resolve(SERVER_PROPERTIES_FILE);
            if (Files.exists(propsPath)) {
                java.util.Properties props = new java.util.Properties();
                try (var reader = Files.newBufferedReader(propsPath)) {
                    props.load(reader);
                }
                props.putAll(configOverrides);
                try (var writer = Files.newBufferedWriter(propsPath)) {
                    props.store(writer, "Generated by Numdrassl Server Manager");
                }
            }
        }

        // Make startup script executable
        Path startupScript = destination.resolve(STARTUP_SCRIPT);
        if (Files.exists(startupScript)) {
            try {
                startupScript.toFile().setExecutable(true);
            } catch (SecurityException e) {
                LOGGER.warn("Could not make startup.sh executable: {}", e.getMessage());
            }
        }

        LOGGER.info("Cloned template '{}' to {}", name, destination);
    }

    /**
     * Get the template name.
     *
     * @return template name
     */
    @Nonnull
    public String getName() {
        return name;
    }

    /**
     * Get the template directory path.
     *
     * @return template path
     */
    @Nonnull
    public Path getTemplatePath() {
        return templatePath;
    }

    /**
     * Get the template metadata.
     *
     * @return metadata, or null if not loaded
     */
    @Nullable
    public TemplateMetadata getMetadata() {
        return metadata;
    }

    /**
     * Get validation errors from last validate() call.
     *
     * @return list of validation errors
     */
    @Nonnull
    public List<String> getValidationErrors() {
        return new ArrayList<>(validationErrors);
    }

    /**
     * Check if template has a startup script.
     *
     * @return true if startup.sh exists
     */
    public boolean hasStartupScript() {
        return Files.exists(templatePath.resolve(STARTUP_SCRIPT));
    }

    /**
     * Check if template has a world directory.
     *
     * @return true if world/ exists
     */
    public boolean hasWorld() {
        return Files.isDirectory(templatePath.resolve(WORLD_DIR));
    }

    /**
     * Check if template has a plugins directory.
     *
     * @return true if plugins/ exists
     */
    public boolean hasPlugins() {
        return Files.isDirectory(templatePath.resolve(PLUGINS_DIR));
    }

    @Override
    public String toString() {
        return "ServerTemplate{" +
                "name='" + name + '\'' +
                ", path=" + templatePath +
                ", valid=" + validationErrors.isEmpty() +
                '}';
    }
}

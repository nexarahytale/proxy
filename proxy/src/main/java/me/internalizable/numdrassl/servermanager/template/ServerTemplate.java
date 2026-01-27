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
 * all files needed to spawn a dynamic Hytale server instance.</p>
 *
 * <h2>Required Structure (Hytale Server)</h2>
 * <pre>
 * servers/templates/bedwars/
 * ├── template.yml          # Template metadata (required)
 * ├── HytaleServer.jar      # Hytale server JAR (required)
 * ├── Assets.zip            # Game assets (required)
 * ├── config.json           # Hytale server config (optional, auto-generated)
 * ├── plugins/              # Plugin directory (optional)
 * │   └── bridge.jar        # Numdrassl Bridge plugin
 * └── universe/             # World data (optional)
 * </pre>
 *
 * <h2>Hytale Server Startup</h2>
 * <pre>
 * java -XX:AOTCache=HytaleServer.aot -jar HytaleServer.jar --assets Assets.zip --bind PORT
 * </pre>
 */
public class ServerTemplate {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerTemplate.class);

    private static final String TEMPLATE_METADATA_FILE = "template.yml";
    private static final String CONFIG_JSON_FILE = "config.json";
    private static final String HYTALE_SERVER_JAR = "HytaleServer.jar";
    private static final String ASSETS_ZIP = "Assets.zip";
    private static final String STARTUP_SCRIPT = "startup.sh";
    private static final String PLUGINS_DIR = "plugins";
    private static final String UNIVERSE_DIR = "universe";
    private static final String BRIDGE_CONFIG_DIR = "plugins/Bridge";

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
     * Validate the template structure for Hytale servers.
     *
     * @return true if template is valid
     */
    public boolean validate() {
        validationErrors.clear();

        if (!Files.isDirectory(templatePath)) {
            validationErrors.add("Template directory does not exist: " + templatePath);
            return false;
        }

        // Check for template.yml (recommended)
        Path templateYml = templatePath.resolve(TEMPLATE_METADATA_FILE);
        if (!Files.exists(templateYml)) {
            LOGGER.warn("Template '{}' missing template.yml, using defaults", name);
        }

        // Check for HytaleServer.jar
        String serverJar = metadata != null && metadata.getServerJar() != null 
                ? metadata.getServerJar() 
                : "HytaleServer.jar";
        Path serverJarPath = templatePath.resolve(serverJar);
        if (!Files.exists(serverJarPath)) {
            // Look for any server jar
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(templatePath, "*.jar")) {
                boolean foundJar = false;
                for (Path jar : stream) {
                    String jarName = jar.getFileName().toString().toLowerCase();
                    if (jarName.contains("server") || jarName.contains("hytale")) {
                        foundJar = true;
                        break;
                    }
                }
                if (!foundJar) {
                    validationErrors.add("Missing HytaleServer.jar or equivalent server JAR");
                }
            } catch (IOException e) {
                validationErrors.add("Failed to scan for JAR files: " + e.getMessage());
            }
        }

        // Check for Assets.zip (required for Hytale)
        Path assetsZip = templatePath.resolve("Assets.zip");
        if (!Files.exists(assetsZip)) {
            LOGGER.warn("Template '{}' missing Assets.zip - server may fail to start", name);
        }

        // Check plugins directory for Bridge
        Path pluginsDir = templatePath.resolve(PLUGINS_DIR);
        if (Files.isDirectory(pluginsDir)) {
            Path bridgeJar = pluginsDir.resolve("bridge-1.0-SNAPSHOT.jar");
            if (!Files.exists(bridgeJar)) {
                LOGGER.warn("Template '{}' missing bridge-1.0-SNAPSHOT.jar in plugins/", name);
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
     * @param configOverrides properties containing server-id, port, etc.
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

        // Generate/update Hytale config.json if needed
        if (configOverrides != null) {
            String serverId = configOverrides.getProperty("server-id");
            String maxPlayers = configOverrides.getProperty("max-players", "100");
            
            // Create config.json for Hytale server
            Path configPath = destination.resolve(CONFIG_JSON_FILE);
            if (!Files.exists(configPath)) {
                String configJson = String.format("""
                    {
                      "Version": 3,
                      "ServerName": "%s",
                      "MOTD": "",
                      "Password": "",
                      "MaxPlayers": %s,
                      "MaxViewRadius": 32,
                      "Defaults": {
                        "World": "default",
                        "GameMode": "Adventure"
                      },
                      "ConnectionTimeouts": {
                        "JoinTimeouts": {}
                      },
                      "RateLimit": {},
                      "Modules": {},
                      "LogLevels": {},
                      "Mods": {},
                      "DisplayTmpTagsInStrings": false,
                      "PlayerStorage": {
                        "Type": "Hytale"
                      }
                    }
                    """, serverId != null ? serverId : name, maxPlayers);
                Files.writeString(configPath, configJson);
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
     * Check if template has a universe directory (Hytale world data).
     *
     * @return true if universe/ exists
     */
    public boolean hasUniverse() {
        return Files.isDirectory(templatePath.resolve(UNIVERSE_DIR));
    }

    /**
     * Check if template has a world directory (legacy).
     *
     * @return true if universe/ exists
     */
    public boolean hasWorld() {
        return hasUniverse();
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

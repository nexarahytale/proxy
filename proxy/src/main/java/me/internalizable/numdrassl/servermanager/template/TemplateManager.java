package me.internalizable.numdrassl.servermanager.template;

import me.internalizable.numdrassl.servermanager.config.ServerManagerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages server templates on disk.
 *
 * <p>Discovers templates in {@code servers/templates/}, validates their
 * structure, and provides cloning functionality for dynamic server spawning.</p>
 */
public class TemplateManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateManager.class);

    private final Path templatesDirectory;
    private final ServerManagerConfig config;
    private final Map<String, ServerTemplate> templates = new ConcurrentHashMap<>();

    /**
     * Create a template manager.
     *
     * @param templatesDirectory path to templates directory
     * @param config server manager configuration
     */
    public TemplateManager(@Nonnull Path templatesDirectory, @Nonnull ServerManagerConfig config) {
        this.templatesDirectory = Objects.requireNonNull(templatesDirectory, "templatesDirectory");
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Initialize the template manager and discover all templates.
     *
     * @throws IOException if initialization fails
     */
    public void initialize() throws IOException {
        ensureDirectoryExists();
        loadAllTemplates();
    }

    private void ensureDirectoryExists() throws IOException {
        if (!Files.exists(templatesDirectory)) {
            Files.createDirectories(templatesDirectory);
            LOGGER.info("Created templates directory: {}", templatesDirectory);
        }
    }

    /**
     * Load all templates from the templates directory.
     *
     * @return number of templates loaded
     */
    public int loadAllTemplates() {
        templates.clear();
        int loaded = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(templatesDirectory)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    String name = entry.getFileName().toString();
                    try {
                        ServerTemplate template = ServerTemplate.loadFromDisk(entry);
                        if (template.validate()) {
                            templates.put(name.toLowerCase(), template);
                            loaded++;
                            LOGGER.info("Loaded template: {}", name);
                        } else {
                            LOGGER.warn("Template '{}' failed validation: {}",
                                    name, template.getValidationErrors());
                        }
                    } catch (IOException e) {
                        LOGGER.error("Failed to load template '{}': {}", name, e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to scan templates directory: {}", e.getMessage());
        }

        // Also register templates from config that may not have disk directories yet
        for (String templateName : config.getTemplates().keySet()) {
            if (!templates.containsKey(templateName.toLowerCase())) {
                LOGGER.warn("Template '{}' defined in config but not found on disk", templateName);
            }
        }

        LOGGER.info("Loaded {} template(s)", loaded);
        return loaded;
    }

    /**
     * Get a template by name.
     *
     * @param name template name (case-insensitive)
     * @return the template, or null if not found
     */
    @Nullable
    public ServerTemplate getTemplate(@Nonnull String name) {
        Objects.requireNonNull(name, "name");
        return templates.get(name.toLowerCase());
    }

    /**
     * Check if a template exists.
     *
     * @param name template name
     * @return true if template exists and is valid
     */
    public boolean hasTemplate(@Nonnull String name) {
        return templates.containsKey(name.toLowerCase());
    }

    /**
     * Get all loaded templates.
     *
     * @return unmodifiable collection of templates
     */
    @Nonnull
    public Collection<ServerTemplate> getAllTemplates() {
        return Collections.unmodifiableCollection(templates.values());
    }

    /**
     * Get all template names.
     *
     * @return unmodifiable set of template names
     */
    @Nonnull
    public Set<String> getTemplateNames() {
        return Collections.unmodifiableSet(templates.keySet());
    }

    /**
     * Validate a template at the given path.
     *
     * @param path path to template directory
     * @return validation result with errors if any
     */
    @Nonnull
    public TemplateValidationResult validateTemplate(@Nonnull Path path) {
        Objects.requireNonNull(path, "path");

        if (!Files.isDirectory(path)) {
            return new TemplateValidationResult(false, List.of("Not a directory: " + path));
        }

        try {
            ServerTemplate template = ServerTemplate.loadFromDisk(path);
            boolean valid = template.validate();
            return new TemplateValidationResult(valid, template.getValidationErrors());
        } catch (IOException e) {
            return new TemplateValidationResult(false, List.of("Failed to load: " + e.getMessage()));
        }
    }

    /**
     * Clone a template to a destination directory.
     *
     * @param templateName name of template to clone
     * @param destination target directory
     * @param configOverrides properties to override
     * @throws IOException if cloning fails
     * @throws IllegalArgumentException if template not found
     */
    public void cloneTemplate(@Nonnull String templateName, @Nonnull Path destination,
                              @Nullable Properties configOverrides) throws IOException {
        Objects.requireNonNull(templateName, "templateName");
        Objects.requireNonNull(destination, "destination");

        ServerTemplate template = getTemplate(templateName);
        if (template == null) {
            throw new IllegalArgumentException("Template not found: " + templateName);
        }

        template.cloneTo(destination, configOverrides);
    }

    /**
     * Reload a specific template from disk.
     *
     * @param name template name
     * @return true if reload was successful
     */
    public boolean reloadTemplate(@Nonnull String name) {
        Objects.requireNonNull(name, "name");

        Path templatePath = templatesDirectory.resolve(name);
        if (!Files.isDirectory(templatePath)) {
            templates.remove(name.toLowerCase());
            return false;
        }

        try {
            ServerTemplate template = ServerTemplate.loadFromDisk(templatePath);
            if (template.validate()) {
                templates.put(name.toLowerCase(), template);
                LOGGER.info("Reloaded template: {}", name);
                return true;
            } else {
                LOGGER.warn("Template '{}' failed validation after reload", name);
                return false;
            }
        } catch (IOException e) {
            LOGGER.error("Failed to reload template '{}': {}", name, e.getMessage());
            return false;
        }
    }

    /**
     * Create a new template directory with default structure.
     *
     * @param name template name
     * @return the created template
     * @throws IOException if creation fails
     */
    @Nonnull
    public ServerTemplate createTemplate(@Nonnull String name) throws IOException {
        Objects.requireNonNull(name, "name");

        Path templatePath = templatesDirectory.resolve(name);
        if (Files.exists(templatePath)) {
            throw new IOException("Template already exists: " + name);
        }

        // Create directory structure
        Files.createDirectories(templatePath);
        Files.createDirectories(templatePath.resolve("plugins"));
        Files.createDirectories(templatePath.resolve("world"));

        // Create default template.yml
        TemplateMetadata metadata = new TemplateMetadata();
        metadata.setName(name);
        metadata.setServerIdPrefix(name.toLowerCase());
        metadata.save(templatePath.resolve("template.yml"));

        // Create default server.properties
        Properties props = new Properties();
        props.setProperty("server-port", "6000");
        props.setProperty("server-id", name);
        props.setProperty("server-name", name);
        props.setProperty("max-players", "16");
        props.setProperty("online-mode", "false");
        try (var writer = Files.newBufferedWriter(templatePath.resolve("server.properties"))) {
            props.store(writer, "Hytale Server Configuration - Generated by Numdrassl");
        }

        // Create startup script
        String startupScript = """
                #!/bin/bash
                # Hytale Server Startup Script
                # Generated by Numdrassl Server Manager
                
                MEMORY="${MEMORY:-2G}"
                SERVER_JAR="${SERVER_JAR:-server.jar}"
                
                java -Xms${MEMORY} -Xmx${MEMORY} \\
                    -XX:+UseG1GC \\
                    -XX:+ParallelRefProcEnabled \\
                    -XX:MaxGCPauseMillis=200 \\
                    -jar "${SERVER_JAR}" \\
                    "$@"
                """;
        Files.writeString(templatePath.resolve("startup.sh"), startupScript);
        templatePath.resolve("startup.sh").toFile().setExecutable(true);

        LOGGER.info("Created new template: {}", name);

        // Load and return the template
        ServerTemplate template = ServerTemplate.loadFromDisk(templatePath);
        templates.put(name.toLowerCase(), template);
        return template;
    }

    /**
     * Get the templates directory path.
     *
     * @return templates directory
     */
    @Nonnull
    public Path getTemplatesDirectory() {
        return templatesDirectory;
    }

    /**
     * Result of template validation.
     */
    public record TemplateValidationResult(boolean valid, List<String> errors) {
        public TemplateValidationResult {
            errors = errors != null ? List.copyOf(errors) : List.of();
        }
    }
}

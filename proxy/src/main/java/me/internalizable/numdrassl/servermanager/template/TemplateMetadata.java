package me.internalizable.numdrassl.servermanager.template;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Metadata for a server template, loaded from template.yml.
 *
 * <p>Contains all configuration needed to spawn a dynamic server
 * from this template.</p>
 */
public class TemplateMetadata {

    private String name;
    private String type = "minigame";
    private String serverIdPrefix;
    private int maxPlayers = 16;
    private String memoryAllocation = "2G";
    private boolean worldResetOnShutdown = true;
    private String pluginLoader = "plugins/bridge-1.0-SNAPSHOT.jar";
    private String startupCommand = "./startup.sh";
    private int gracefulShutdownTimeout = 30;
    private Map<String, Double> respawnLocation = new HashMap<>();
    private String serverJar = "HytaleServer.jar";
    
    // Hytale-specific startup arguments (--assets, --auth-mode, --transport, --bind)
    private List<String> startupArgs = new ArrayList<>();

    public TemplateMetadata() {
        respawnLocation.put("x", 0.0);
        respawnLocation.put("y", 64.0);
        respawnLocation.put("z", 0.0);
    }

    /**
     * Load template metadata from file.
     *
     * @param path path to template.yml
     * @return loaded metadata
     * @throws IOException if loading fails
     */
    @Nonnull
    public static TemplateMetadata load(@Nonnull Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("Template metadata not found: " + path);
        }

        LoaderOptions options = new LoaderOptions();
        Yaml yaml = new Yaml(new Constructor(TemplateMetadata.class, options));
        try (InputStream is = Files.newInputStream(path)) {
            TemplateMetadata metadata = yaml.load(is);
            return metadata != null ? metadata : new TemplateMetadata();
        }
    }

    /**
     * Save template metadata to file.
     *
     * @param path path to save to
     * @throws IOException if saving fails
     */
    public void save(@Nonnull Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Yaml yaml = new Yaml();
        try (Writer writer = Files.newBufferedWriter(path)) {
            yaml.dump(this, writer);
        }
    }

    // Getters and Setters

    @Nullable
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Nonnull
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Nullable
    public String getServerIdPrefix() {
        return serverIdPrefix;
    }

    public void setServerIdPrefix(String serverIdPrefix) {
        this.serverIdPrefix = serverIdPrefix;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    @Nonnull
    public String getMemoryAllocation() {
        return memoryAllocation;
    }

    public void setMemoryAllocation(String memoryAllocation) {
        this.memoryAllocation = memoryAllocation;
    }

    public boolean isWorldResetOnShutdown() {
        return worldResetOnShutdown;
    }

    public void setWorldResetOnShutdown(boolean worldResetOnShutdown) {
        this.worldResetOnShutdown = worldResetOnShutdown;
    }

    @Nonnull
    public String getPluginLoader() {
        return pluginLoader;
    }

    public void setPluginLoader(String pluginLoader) {
        this.pluginLoader = pluginLoader;
    }

    @Nonnull
    public String getStartupCommand() {
        return startupCommand;
    }

    public void setStartupCommand(String startupCommand) {
        this.startupCommand = startupCommand;
    }

    public int getGracefulShutdownTimeout() {
        return gracefulShutdownTimeout;
    }

    public void setGracefulShutdownTimeout(int gracefulShutdownTimeout) {
        this.gracefulShutdownTimeout = gracefulShutdownTimeout;
    }

    @Nonnull
    public Map<String, Double> getRespawnLocation() {
        return respawnLocation;
    }

    public void setRespawnLocation(Map<String, Double> respawnLocation) {
        this.respawnLocation = respawnLocation;
    }

    @Nullable
    public String getServerJar() {
        return serverJar;
    }

    public void setServerJar(String serverJar) {
        this.serverJar = serverJar;
    }

    /**
     * Get Hytale server startup arguments.
     * @return startup arguments list
     */
    @Nonnull
    public List<String> getStartupArgs() {
        return startupArgs;
    }

    public void setStartupArgs(List<String> startupArgs) {
        this.startupArgs = startupArgs != null ? startupArgs : new ArrayList<>();
    }

    /**
     * Get the effective server ID prefix.
     *
     * @return prefix or name if not set
     */
    @Nonnull
    public String getEffectiveServerIdPrefix() {
        if (serverIdPrefix != null && !serverIdPrefix.isEmpty()) {
            return serverIdPrefix;
        }
        if (name != null && !name.isEmpty()) {
            return name.toLowerCase().replaceAll("[^a-z0-9]", "-");
        }
        return "server";
    }
}

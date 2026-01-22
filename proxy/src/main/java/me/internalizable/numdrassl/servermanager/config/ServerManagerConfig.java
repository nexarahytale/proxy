package me.internalizable.numdrassl.servermanager.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import javax.annotation.Nonnull;
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
 * Configuration for the server management system.
 *
 * <p>Loaded from {@code servers/config.yml} and defines static servers,
 * template settings, and dynamic spawning parameters.</p>
 */
public class ServerManagerConfig {

    private Map<String, StaticServerConfig> staticServers = new HashMap<>();
    private Map<String, TemplateConfig> templates = new HashMap<>();
    private DynamicSpawningConfig dynamicSpawning = new DynamicSpawningConfig();
    private PortAllocationConfig portAllocation = new PortAllocationConfig();
    private String javaPath = "java";
    private String defaultFallbackServer = "lobby";
    private int healthCheckIntervalSeconds = 30;
    private int processStartTimeoutSeconds = 60;

    public ServerManagerConfig() {
        // Default static server
        StaticServerConfig lobby = new StaticServerConfig();
        lobby.setPort(6000);
        lobby.setMaxPlayers(100);
        lobby.setAlwaysOn(true);
        lobby.setWorldFolder("servers/static/lobby");
        staticServers.put("lobby", lobby);

        // Default template
        TemplateConfig bedwars = new TemplateConfig();
        bedwars.setDisplayName("Bedwars");
        bedwars.setMaxPlayers(16);
        bedwars.setPortRangeStart(6100);
        bedwars.setPortRangeEnd(6200);
        bedwars.setMemory("2G");
        bedwars.setWorldReset(true);
        bedwars.setAutoCleanupDelaySeconds(300);
        templates.put("bedwars", bedwars);
    }

    /**
     * Load configuration from file, creating default if not exists.
     *
     * @param path path to config file
     * @return loaded configuration
     * @throws IOException if loading fails
     */
    @Nonnull
    public static ServerManagerConfig load(@Nonnull Path path) throws IOException {
        if (!Files.exists(path)) {
            ServerManagerConfig config = new ServerManagerConfig();
            config.save(path);
            return config;
        }

        LoaderOptions options = new LoaderOptions();
        Yaml yaml = new Yaml(new Constructor(ServerManagerConfig.class, options));
        try (InputStream is = Files.newInputStream(path)) {
            ServerManagerConfig config = yaml.load(is);
            return config != null ? config : new ServerManagerConfig();
        }
    }

    /**
     * Save configuration to file.
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

    public Map<String, StaticServerConfig> getStaticServers() {
        return staticServers;
    }

    public void setStaticServers(Map<String, StaticServerConfig> staticServers) {
        this.staticServers = staticServers;
    }

    public Map<String, TemplateConfig> getTemplates() {
        return templates;
    }

    public void setTemplates(Map<String, TemplateConfig> templates) {
        this.templates = templates;
    }

    public DynamicSpawningConfig getDynamicSpawning() {
        return dynamicSpawning;
    }

    public void setDynamicSpawning(DynamicSpawningConfig dynamicSpawning) {
        this.dynamicSpawning = dynamicSpawning;
    }

    public PortAllocationConfig getPortAllocation() {
        return portAllocation;
    }

    public void setPortAllocation(PortAllocationConfig portAllocation) {
        this.portAllocation = portAllocation;
    }

    public String getJavaPath() {
        return javaPath;
    }

    public void setJavaPath(String javaPath) {
        this.javaPath = javaPath;
    }

    public String getDefaultFallbackServer() {
        return defaultFallbackServer;
    }

    public void setDefaultFallbackServer(String defaultFallbackServer) {
        this.defaultFallbackServer = defaultFallbackServer;
    }

    public int getHealthCheckIntervalSeconds() {
        return healthCheckIntervalSeconds;
    }

    public void setHealthCheckIntervalSeconds(int healthCheckIntervalSeconds) {
        this.healthCheckIntervalSeconds = healthCheckIntervalSeconds;
    }

    public int getProcessStartTimeoutSeconds() {
        return processStartTimeoutSeconds;
    }

    public void setProcessStartTimeoutSeconds(int processStartTimeoutSeconds) {
        this.processStartTimeoutSeconds = processStartTimeoutSeconds;
    }

    /**
     * Configuration for a static (persistent) server.
     */
    public static class StaticServerConfig {
        private int port = 6000;
        private int maxPlayers = 100;
        private boolean alwaysOn = true;
        private String worldFolder;
        private String memory = "2G";
        private List<String> jvmArgs = new ArrayList<>();
        private Map<String, String> environment = new HashMap<>();

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public int getMaxPlayers() {
            return maxPlayers;
        }

        public void setMaxPlayers(int maxPlayers) {
            this.maxPlayers = maxPlayers;
        }

        public boolean isAlwaysOn() {
            return alwaysOn;
        }

        public void setAlwaysOn(boolean alwaysOn) {
            this.alwaysOn = alwaysOn;
        }

        public String getWorldFolder() {
            return worldFolder;
        }

        public void setWorldFolder(String worldFolder) {
            this.worldFolder = worldFolder;
        }

        public String getMemory() {
            return memory;
        }

        public void setMemory(String memory) {
            this.memory = memory;
        }

        public List<String> getJvmArgs() {
            return jvmArgs;
        }

        public void setJvmArgs(List<String> jvmArgs) {
            this.jvmArgs = jvmArgs;
        }

        public Map<String, String> getEnvironment() {
            return environment;
        }

        public void setEnvironment(Map<String, String> environment) {
            this.environment = environment;
        }
    }

    /**
     * Configuration for a server template.
     */
    public static class TemplateConfig {
        private String displayName;
        private int maxPlayers = 16;
        private int portRangeStart = 6100;
        private int portRangeEnd = 6500;
        private String memory = "2G";
        private boolean worldReset = true;
        private int autoCleanupDelaySeconds = 300;
        private int gracefulShutdownTimeoutSeconds = 30;
        private List<String> jvmArgs = new ArrayList<>();
        private Map<String, String> environment = new HashMap<>();

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public int getMaxPlayers() {
            return maxPlayers;
        }

        public void setMaxPlayers(int maxPlayers) {
            this.maxPlayers = maxPlayers;
        }

        public int getPortRangeStart() {
            return portRangeStart;
        }

        public void setPortRangeStart(int portRangeStart) {
            this.portRangeStart = portRangeStart;
        }

        public int getPortRangeEnd() {
            return portRangeEnd;
        }

        public void setPortRangeEnd(int portRangeEnd) {
            this.portRangeEnd = portRangeEnd;
        }

        public String getMemory() {
            return memory;
        }

        public void setMemory(String memory) {
            this.memory = memory;
        }

        public boolean isWorldReset() {
            return worldReset;
        }

        public void setWorldReset(boolean worldReset) {
            this.worldReset = worldReset;
        }

        public int getAutoCleanupDelaySeconds() {
            return autoCleanupDelaySeconds;
        }

        public void setAutoCleanupDelaySeconds(int autoCleanupDelaySeconds) {
            this.autoCleanupDelaySeconds = autoCleanupDelaySeconds;
        }

        public int getGracefulShutdownTimeoutSeconds() {
            return gracefulShutdownTimeoutSeconds;
        }

        public void setGracefulShutdownTimeoutSeconds(int gracefulShutdownTimeoutSeconds) {
            this.gracefulShutdownTimeoutSeconds = gracefulShutdownTimeoutSeconds;
        }

        public List<String> getJvmArgs() {
            return jvmArgs;
        }

        public void setJvmArgs(List<String> jvmArgs) {
            this.jvmArgs = jvmArgs;
        }

        public Map<String, String> getEnvironment() {
            return environment;
        }

        public void setEnvironment(Map<String, String> environment) {
            this.environment = environment;
        }
    }

    /**
     * Configuration for dynamic server spawning.
     */
    public static class DynamicSpawningConfig {
        private boolean enabled = true;
        private boolean autoCleanup = true;
        private int maxConcurrent = 50;
        private int minAvailablePorts = 10;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAutoCleanup() {
            return autoCleanup;
        }

        public void setAutoCleanup(boolean autoCleanup) {
            this.autoCleanup = autoCleanup;
        }

        public int getMaxConcurrent() {
            return maxConcurrent;
        }

        public void setMaxConcurrent(int maxConcurrent) {
            this.maxConcurrent = maxConcurrent;
        }

        public int getMinAvailablePorts() {
            return minAvailablePorts;
        }

        public void setMinAvailablePorts(int minAvailablePorts) {
            this.minAvailablePorts = minAvailablePorts;
        }
    }

    /**
     * Configuration for port allocation.
     */
    public static class PortAllocationConfig {
        private int staticRangeStart = 6000;
        private int staticRangeEnd = 6050;
        private int dynamicRangeStart = 6100;
        private int dynamicRangeEnd = 6500;

        public int getStaticRangeStart() {
            return staticRangeStart;
        }

        public void setStaticRangeStart(int staticRangeStart) {
            this.staticRangeStart = staticRangeStart;
        }

        public int getStaticRangeEnd() {
            return staticRangeEnd;
        }

        public void setStaticRangeEnd(int staticRangeEnd) {
            this.staticRangeEnd = staticRangeEnd;
        }

        public int getDynamicRangeStart() {
            return dynamicRangeStart;
        }

        public void setDynamicRangeStart(int dynamicRangeStart) {
            this.dynamicRangeStart = dynamicRangeStart;
        }

        public int getDynamicRangeEnd() {
            return dynamicRangeEnd;
        }

        public void setDynamicRangeEnd(int dynamicRangeEnd) {
            this.dynamicRangeEnd = dynamicRangeEnd;
        }
    }
}

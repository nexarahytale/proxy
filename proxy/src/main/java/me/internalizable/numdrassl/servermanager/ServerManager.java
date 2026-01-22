package me.internalizable.numdrassl.servermanager;

import me.internalizable.numdrassl.api.server.RegisteredServer;
import me.internalizable.numdrassl.event.api.NumdrasslEventManager;
import me.internalizable.numdrassl.plugin.NumdrasslProxy;
import me.internalizable.numdrassl.plugin.server.NumdrasslRegisteredServer;
import me.internalizable.numdrassl.servermanager.config.ServerManagerConfig;
import me.internalizable.numdrassl.servermanager.event.ServerShutdownEvent;
import me.internalizable.numdrassl.servermanager.event.ServerSpawnEvent;
import me.internalizable.numdrassl.servermanager.instance.InstanceManager;
import me.internalizable.numdrassl.servermanager.instance.ServerInstance;
import me.internalizable.numdrassl.servermanager.instance.ServerStatus;
import me.internalizable.numdrassl.servermanager.process.ProcessManager;
import me.internalizable.numdrassl.servermanager.registry.ServerRegistry;
import me.internalizable.numdrassl.servermanager.registry.ServerType;
import me.internalizable.numdrassl.servermanager.template.ServerTemplate;
import me.internalizable.numdrassl.servermanager.template.TemplateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Main orchestrator for the CloudNet-style server management system.
 *
 * <p>Coordinates template management, instance lifecycle, process spawning,
 * and server registry for both static and dynamic Hytale backend servers.</p>
 *
 * <h2>Directory Structure</h2>
 * <pre>
 * servers/
 * ├── config.yml           # Server management configuration
 * ├── templates/           # Template definitions
 * │   ├── bedwars/
 * │   ├── skywars/
 * │   └── lobby/
 * ├── static/              # Persistent servers
 * │   └── lobby/
 * ├── dynamic/             # Runtime-spawned servers (ephemeral)
 * │   ├── bedwars-1/
 * │   └── skywars-2/
 * └── logs/                # Server logs
 *     ├── static/
 *     └── dynamic/
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ServerManager manager = new ServerManager(proxy);
 * manager.initialize();
 *
 * // Spawn a dynamic server
 * ServerInstance instance = manager.spawnDynamicServer("bedwars",
 *     new SpawnOptions().setMaxPlayers(8)).join();
 *
 * // Shutdown when done
 * manager.shutdownDynamicServer(instance.getServerId()).join();
 * }</pre>
 */
public class ServerManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerManager.class);

    private static final Path SERVERS_ROOT = Paths.get("servers");
    private static final Path CONFIG_PATH = SERVERS_ROOT.resolve("config.yml");
    private static final Path TEMPLATES_DIR = SERVERS_ROOT.resolve("templates");
    private static final Path STATIC_DIR = SERVERS_ROOT.resolve("static");
    private static final Path DYNAMIC_DIR = SERVERS_ROOT.resolve("dynamic");
    private static final Path LOGS_DIR = SERVERS_ROOT.resolve("logs");

    private final NumdrasslProxy proxy;
    private final NumdrasslEventManager eventManager;

    private ServerManagerConfig config;
    private TemplateManager templateManager;
    private InstanceManager instanceManager;
    private ProcessManager processManager;
    private ServerRegistry registry;

    private volatile boolean initialized = false;
    private volatile boolean shutdown = false;

    /**
     * Create a server manager.
     *
     * @param proxy the proxy instance
     */
    public ServerManager(@Nonnull NumdrasslProxy proxy) {
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.eventManager = proxy.getNumdrasslEventManager();
    }

    // ==================== Initialization ====================

    /**
     * Initialize the server management system.
     *
     * @throws IOException if initialization fails
     */
    public void initialize() throws IOException {
        if (initialized) {
            throw new IllegalStateException("Server manager already initialized");
        }

        LOGGER.info("Initializing server management system...");

        // Create directory structure
        createDirectoryStructure();

        // Load configuration
        config = ServerManagerConfig.load(CONFIG_PATH);
        LOGGER.info("Loaded server manager configuration");

        // Initialize components
        registry = new ServerRegistry();

        processManager = new ProcessManager(LOGS_DIR, config.getJavaPath());
        processManager.initialize();

        templateManager = new TemplateManager(TEMPLATES_DIR, config);
        templateManager.initialize();

        instanceManager = new InstanceManager(
                config,
                templateManager,
                processManager,
                STATIC_DIR,
                DYNAMIC_DIR
        );
        instanceManager.initialize();

        // Load static servers
        loadStaticServers();

        initialized = true;
        LOGGER.info("Server management system initialized");
        LOGGER.info("  Templates: {}", templateManager.getTemplateNames().size());
        LOGGER.info("  Static servers: {}", registry.getStaticServers().size());
    }

    private void createDirectoryStructure() throws IOException {
        Files.createDirectories(SERVERS_ROOT);
        Files.createDirectories(TEMPLATES_DIR);
        Files.createDirectories(STATIC_DIR);
        Files.createDirectories(DYNAMIC_DIR);
        Files.createDirectories(LOGS_DIR);
        Files.createDirectories(LOGS_DIR.resolve("static"));
        Files.createDirectories(LOGS_DIR.resolve("dynamic"));

        LOGGER.debug("Created server directory structure");
    }

    private void loadStaticServers() {
        for (Map.Entry<String, ServerManagerConfig.StaticServerConfig> entry :
                config.getStaticServers().entrySet()) {

            String serverId = entry.getKey();
            ServerManagerConfig.StaticServerConfig staticConfig = entry.getValue();

            Path serverDir = STATIC_DIR.resolve(serverId);
            if (!Files.isDirectory(serverDir)) {
                LOGGER.warn("Static server directory not found: {} (skipping)", serverDir);
                continue;
            }

            if (staticConfig.isAlwaysOn()) {
                LOGGER.info("Starting static server: {}", serverId);
                startStaticServer(serverId).exceptionally(e -> {
                    LOGGER.error("Failed to start static server '{}': {}", serverId, e.getMessage());
                    return null;
                });
            } else {
                LOGGER.info("Static server '{}' configured but not always-on", serverId);
            }
        }
    }

    // ==================== Static Server Management ====================

    /**
     * Start a static server.
     *
     * @param serverId server identifier
     * @return future completing with the server instance
     */
    @Nonnull
    public CompletableFuture<ServerInstance> startStaticServer(@Nonnull String serverId) {
        checkInitialized();
        Objects.requireNonNull(serverId, "serverId");

        ServerManagerConfig.StaticServerConfig staticConfig =
                config.getStaticServers().get(serverId);
        if (staticConfig == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Static server not configured: " + serverId));
        }

        return instanceManager.startStaticServer(serverId, staticConfig)
                .thenApply(instance -> {
                    registry.register(instance);
                    registerWithProxy(instance);
                    eventManager.fireSync(new ServerSpawnEvent(instance));
                    return instance;
                });
    }

    // ==================== Dynamic Server Management ====================

    /**
     * Spawn a dynamic server from a template.
     *
     * @param templateName template name
     * @param options spawn options
     * @return future completing with the server instance
     */
    @Nonnull
    public CompletableFuture<ServerInstance> spawnDynamicServer(
            @Nonnull String templateName,
            @Nonnull InstanceManager.SpawnOptions options) {
        checkInitialized();
        Objects.requireNonNull(templateName, "templateName");
        Objects.requireNonNull(options, "options");

        if (!templateManager.hasTemplate(templateName)) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Template not found: " + templateName));
        }

        return instanceManager.spawnDynamicServer(templateName, options)
                .thenApply(instance -> {
                    registry.register(instance);
                    registerWithProxy(instance);
                    eventManager.fireSync(new ServerSpawnEvent(instance));
                    LOGGER.info("Dynamic server '{}' spawned from template '{}'",
                            instance.getServerId(), templateName);
                    return instance;
                });
    }

    /**
     * Spawn a dynamic server with default options.
     *
     * @param templateName template name
     * @return future completing with the server instance
     */
    @Nonnull
    public CompletableFuture<ServerInstance> spawnDynamicServer(@Nonnull String templateName) {
        return spawnDynamicServer(templateName, new InstanceManager.SpawnOptions());
    }

    // ==================== Server Shutdown ====================

    /**
     * Shutdown a dynamic server.
     *
     * @param serverId server identifier
     * @return future completing when shutdown is done
     */
    @Nonnull
    public CompletableFuture<Void> shutdownDynamicServer(@Nonnull String serverId) {
        return shutdownServer(serverId, false);
    }

    /**
     * Shutdown a server (static or dynamic).
     *
     * @param serverId server identifier
     * @param force force immediate shutdown
     * @return future completing when shutdown is done
     */
    @Nonnull
    public CompletableFuture<Void> shutdownServer(@Nonnull String serverId, boolean force) {
        checkInitialized();
        Objects.requireNonNull(serverId, "serverId");

        ServerInstance instance = registry.getServer(serverId);
        if (instance == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Server not found: " + serverId));
        }

        // Fire shutdown event
        ServerShutdownEvent.ShutdownReason reason = force
                ? ServerShutdownEvent.ShutdownReason.ADMIN_REQUEST
                : ServerShutdownEvent.ShutdownReason.ADMIN_REQUEST;
        eventManager.fireSync(new ServerShutdownEvent(instance, reason, force));

        return instanceManager.shutdownServer(serverId, force)
                .thenRun(() -> {
                    registry.unregister(serverId);
                    unregisterFromProxy(serverId);
                    LOGGER.info("Server '{}' shut down", serverId);
                });
    }

    /**
     * Restart a server.
     *
     * @param serverId server identifier
     * @return future completing with the restarted instance
     */
    @Nonnull
    public CompletableFuture<ServerInstance> restartServer(@Nonnull String serverId) {
        checkInitialized();
        Objects.requireNonNull(serverId, "serverId");

        ServerInstance instance = registry.getServer(serverId);
        if (instance == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Server not found: " + serverId));
        }

        if (instance.isStatic()) {
            return shutdownServer(serverId, false)
                    .thenCompose(v -> startStaticServer(serverId));
        } else {
            String templateName = instance.getTemplate() != null
                    ? instance.getTemplate().getName()
                    : null;
            if (templateName == null) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Cannot restart dynamic server without template"));
            }

            InstanceManager.SpawnOptions options = new InstanceManager.SpawnOptions()
                    .setServerId(serverId)
                    .setMaxPlayers(instance.getMaxPlayers());

            return shutdownServer(serverId, false)
                    .thenCompose(v -> spawnDynamicServer(templateName, options));
        }
    }

    // ==================== Proxy Integration ====================

    private void registerWithProxy(ServerInstance instance) {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", instance.getPort());
        NumdrasslRegisteredServer server = new NumdrasslRegisteredServer(
                instance.getServerId(), address);

        // Register with proxy's server list
        proxy.registerServer(instance.getServerId(), address);

        LOGGER.debug("Registered server '{}' with proxy at port {}",
                instance.getServerId(), instance.getPort());
    }

    private void unregisterFromProxy(String serverId) {
        proxy.unregisterServer(serverId);
        LOGGER.debug("Unregistered server '{}' from proxy", serverId);
    }

    // ==================== Queries ====================

    /**
     * Get a server instance by ID.
     *
     * @param serverId server identifier
     * @return the server instance, or null if not found
     */
    @Nullable
    public ServerInstance getServer(@Nonnull String serverId) {
        checkInitialized();
        return registry.getServer(serverId);
    }

    /**
     * Get all server instances.
     *
     * @return unmodifiable collection of instances
     */
    @Nonnull
    public Collection<ServerInstance> getAllServers() {
        checkInitialized();
        return registry.getAllServers();
    }

    /**
     * List servers by type.
     *
     * @param type server type (STATIC, DYNAMIC, or null for all)
     * @return list of matching servers
     */
    @Nonnull
    public List<ServerInstance> listServers(@Nullable ServerType type) {
        checkInitialized();
        if (type == null) {
            return new ArrayList<>(registry.getAllServers());
        }
        return registry.getServersByType(type);
    }

    /**
     * Get a template by name.
     *
     * @param name template name
     * @return the template, or null if not found
     */
    @Nullable
    public ServerTemplate getTemplate(@Nonnull String name) {
        checkInitialized();
        return templateManager.getTemplate(name);
    }

    /**
     * Get all templates.
     *
     * @return collection of templates
     */
    @Nonnull
    public Collection<ServerTemplate> getAllTemplates() {
        checkInitialized();
        return templateManager.getAllTemplates();
    }

    /**
     * Get registry statistics.
     *
     * @return statistics
     */
    @Nonnull
    public ServerRegistry.RegistryStats getStats() {
        checkInitialized();
        return registry.getStats();
    }

    /**
     * Get the configuration.
     *
     * @return server manager configuration
     */
    @Nonnull
    public ServerManagerConfig getConfig() {
        checkInitialized();
        return config;
    }

    /**
     * Get the template manager.
     *
     * @return template manager
     */
    @Nonnull
    public TemplateManager getTemplateManager() {
        checkInitialized();
        return templateManager;
    }

    /**
     * Get the instance manager.
     *
     * @return instance manager
     */
    @Nonnull
    public InstanceManager getInstanceManager() {
        checkInitialized();
        return instanceManager;
    }

    /**
     * Get the process manager.
     *
     * @return process manager
     */
    @Nonnull
    public ProcessManager getProcessManager() {
        checkInitialized();
        return processManager;
    }

    /**
     * Get the server registry.
     *
     * @return server registry
     */
    @Nonnull
    public ServerRegistry getRegistry() {
        checkInitialized();
        return registry;
    }

    // ==================== Lifecycle ====================

    /**
     * Check if the server manager is initialized.
     *
     * @return true if initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    private void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException("Server manager not initialized");
        }
    }

    /**
     * Reload configuration and templates.
     *
     * @throws IOException if reload fails
     */
    public void reload() throws IOException {
        checkInitialized();
        LOGGER.info("Reloading server manager...");

        config = ServerManagerConfig.load(CONFIG_PATH);
        templateManager.loadAllTemplates();

        LOGGER.info("Server manager reloaded");
    }

    /**
     * Shutdown the server management system.
     */
    public void shutdown() {
        if (!initialized || shutdown) {
            return;
        }

        shutdown = true;
        LOGGER.info("Shutting down server management system...");

        // Shutdown all dynamic servers
        for (ServerInstance instance : registry.getDynamicServers()) {
            try {
                eventManager.fireSync(new ServerShutdownEvent(
                        instance,
                        ServerShutdownEvent.ShutdownReason.PROXY_SHUTDOWN,
                        false
                ));
            } catch (Exception e) {
                LOGGER.warn("Error firing shutdown event for '{}': {}",
                        instance.getServerId(), e.getMessage());
            }
        }

        // Shutdown managers
        if (instanceManager != null) {
            instanceManager.shutdown();
        }
        if (processManager != null) {
            processManager.shutdown();
        }

        // Clear registry
        if (registry != null) {
            registry.clear();
        }

        LOGGER.info("Server management system shut down");
    }
}

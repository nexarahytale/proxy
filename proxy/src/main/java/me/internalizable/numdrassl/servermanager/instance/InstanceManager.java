package me.internalizable.numdrassl.servermanager.instance;

import me.internalizable.numdrassl.servermanager.config.ServerManagerConfig;
import me.internalizable.numdrassl.servermanager.process.ManagedProcess;
import me.internalizable.numdrassl.servermanager.process.ProcessManager;
import me.internalizable.numdrassl.servermanager.registry.ServerType;
import me.internalizable.numdrassl.servermanager.template.ServerTemplate;
import me.internalizable.numdrassl.servermanager.template.TemplateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages server instance lifecycle.
 *
 * <p>Handles spawning, monitoring, and cleanup of both static and
 * dynamic server instances.</p>
 */
public class InstanceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstanceManager.class);

    private final ServerManagerConfig config;
    private final TemplateManager templateManager;
    private final ProcessManager processManager;
    private final Path staticDirectory;
    private final Path dynamicDirectory;

    private final Map<String, ServerInstance> instances = new ConcurrentHashMap<>();
    private final Set<Integer> allocatedPorts = ConcurrentHashMap.newKeySet();
    private final AtomicLong instanceCounter = new AtomicLong(0);

    private final ScheduledExecutorService scheduler;
    private final ExecutorService asyncExecutor;

    private volatile boolean shutdown = false;

    /**
     * Create an instance manager.
     *
     * @param config server manager configuration
     * @param templateManager template manager
     * @param processManager process manager
     * @param staticDirectory directory for static servers
     * @param dynamicDirectory directory for dynamic servers
     */
    public InstanceManager(
            @Nonnull ServerManagerConfig config,
            @Nonnull TemplateManager templateManager,
            @Nonnull ProcessManager processManager,
            @Nonnull Path staticDirectory,
            @Nonnull Path dynamicDirectory) {
        this.config = Objects.requireNonNull(config, "config");
        this.templateManager = Objects.requireNonNull(templateManager, "templateManager");
        this.processManager = Objects.requireNonNull(processManager, "processManager");
        this.staticDirectory = Objects.requireNonNull(staticDirectory, "staticDirectory");
        this.dynamicDirectory = Objects.requireNonNull(dynamicDirectory, "dynamicDirectory");

        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "InstanceManager-Scheduler");
            t.setDaemon(true);
            return t;
        });

        this.asyncExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "InstanceManager-Async");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Initialize the instance manager.
     *
     * @throws IOException if initialization fails
     */
    public void initialize() throws IOException {
        Files.createDirectories(staticDirectory);
        Files.createDirectories(dynamicDirectory);

        // Clean up any leftover dynamic server directories
        cleanupDynamicDirectory();

        // Start health check scheduler
        scheduler.scheduleAtFixedRate(
                this::performHealthChecks,
                config.getHealthCheckIntervalSeconds(),
                config.getHealthCheckIntervalSeconds(),
                TimeUnit.SECONDS
        );

        LOGGER.info("Instance manager initialized");
    }

    private void cleanupDynamicDirectory() {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dynamicDirectory)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    LOGGER.info("Cleaning up leftover dynamic server: {}", entry.getFileName());
                    deleteDirectory(entry);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to clean up dynamic directory: {}", e.getMessage());
        }
    }

    // ==================== Static Server Management ====================

    /**
     * Start a static server.
     *
     * @param serverId server identifier
     * @param staticConfig static server configuration
     * @return the server instance
     * @throws IOException if starting fails
     */
    @Nonnull
    public CompletableFuture<ServerInstance> startStaticServer(
            @Nonnull String serverId,
            @Nonnull ServerManagerConfig.StaticServerConfig staticConfig) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                Path serverDir = staticDirectory.resolve(serverId);
                if (!Files.isDirectory(serverDir)) {
                    throw new IOException("Static server directory not found: " + serverDir);
                }

                // Check if already running
                if (instances.containsKey(serverId)) {
                    throw new IllegalStateException("Server already running: " + serverId);
                }

                // Allocate port
                int port = staticConfig.getPort();
                if (allocatedPorts.contains(port)) {
                    throw new IllegalStateException("Port already in use: " + port);
                }
                allocatedPorts.add(port);

                // Create instance
                ServerInstance instance = new ServerInstance(
                        serverId,
                        ServerType.STATIC,
                        serverDir,
                        port,
                        staticConfig.getMaxPlayers(),
                        null
                );

                // Build Hytale server arguments
                List<String> serverArgs = new ArrayList<>();
                if (staticConfig.getServerArgs() != null && !staticConfig.getServerArgs().isEmpty()) {
                    serverArgs.addAll(staticConfig.getServerArgs());
                } else {
                    // Default Hytale server arguments
                    serverArgs.add("--assets");
                    serverArgs.add("Assets.zip");
                    serverArgs.add("--auth-mode");
                    serverArgs.add("insecure");
                    serverArgs.add("--transport");
                    serverArgs.add("QUIC");
                }
                
                // Add --bind for port (Hytale uses --bind to set port)
                serverArgs.add("--bind");
                serverArgs.add(String.valueOf(port));

                // Start process
                ManagedProcess process = processManager.spawnServer(
                        serverId,
                        serverDir,
                        staticConfig.getMemory(),
                        null,
                        staticConfig.getJvmArgs(),
                        serverArgs,
                        staticConfig.getEnvironment(),
                        false
                );

                instance.markStarting(process);
                instances.put(serverId, instance);

                // Wait for server to be ready
                waitForServerReady(instance);

                LOGGER.info("Started static server: {} on port {}", serverId, port);
                return instance;

            } catch (Exception e) {
                LOGGER.error("Failed to start static server '{}': {}", serverId, e.getMessage());
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    // ==================== Dynamic Server Management ====================

    /**
     * Spawn a dynamic server from a template.
     *
     * @param templateName template name
     * @param options spawn options
     * @return the server instance
     */
    @Nonnull
    public CompletableFuture<ServerInstance> spawnDynamicServer(
            @Nonnull String templateName,
            @Nonnull SpawnOptions options) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!config.getDynamicSpawning().isEnabled()) {
                    throw new IllegalStateException("Dynamic spawning is disabled");
                }

                // Check concurrent limit
                long dynamicCount = instances.values().stream()
                        .filter(ServerInstance::isDynamic)
                        .count();
                if (dynamicCount >= config.getDynamicSpawning().getMaxConcurrent()) {
                    throw new IllegalStateException("Maximum concurrent dynamic servers reached");
                }

                // Get template
                ServerTemplate template = templateManager.getTemplate(templateName);
                if (template == null) {
                    throw new IllegalArgumentException("Template not found: " + templateName);
                }

                // Get template config
                ServerManagerConfig.TemplateConfig templateConfig =
                        config.getTemplates().get(templateName.toLowerCase());
                if (templateConfig == null) {
                    templateConfig = new ServerManagerConfig.TemplateConfig();
                }

                // Generate server ID
                String serverId = options.getServerId() != null
                        ? options.getServerId()
                        : generateServerId(template);

                if (instances.containsKey(serverId)) {
                    throw new IllegalStateException("Server ID already in use: " + serverId);
                }

                // Allocate port
                int port = allocatePort(templateConfig.getPortRangeStart(), templateConfig.getPortRangeEnd());
                if (port == -1) {
                    throw new IllegalStateException("No available ports in range");
                }

                // Clone template to dynamic directory
                Path serverDir = dynamicDirectory.resolve(serverId);
                Properties configOverrides = new Properties();
                configOverrides.setProperty("server-port", String.valueOf(port));
                configOverrides.setProperty("server-id", serverId);
                configOverrides.setProperty("max-players", String.valueOf(
                        options.getMaxPlayers() > 0 ? options.getMaxPlayers() : templateConfig.getMaxPlayers()));

                template.cloneTo(serverDir, configOverrides);

                // Create instance
                int maxPlayers = options.getMaxPlayers() > 0
                        ? options.getMaxPlayers()
                        : templateConfig.getMaxPlayers();

                ServerInstance instance = new ServerInstance(
                        serverId,
                        ServerType.DYNAMIC,
                        serverDir,
                        port,
                        maxPlayers,
                        template
                );

                // Set metadata
                if (options.getMetadata() != null) {
                    options.getMetadata().forEach(instance::setMetadata);
                }

                // Prepare environment
                Map<String, String> environment = new HashMap<>(templateConfig.getEnvironment());
                environment.put("NUMDRASSL_SERVER_ID", serverId);
                environment.put("NUMDRASSL_PORT", String.valueOf(port));
                environment.put("NUMDRASSL_TEMPLATE", templateName);

                // Build Hytale server arguments
                List<String> serverArgs = new ArrayList<>();
                
                // Get startup args from template metadata
                if (template.getMetadata() != null && template.getMetadata().getStartupArgs() != null) {
                    serverArgs.addAll(template.getMetadata().getStartupArgs());
                } else {
                    // Default Hytale server arguments
                    serverArgs.add("--assets");
                    serverArgs.add("Assets.zip");
                    serverArgs.add("--auth-mode");
                    serverArgs.add("insecure");
                    serverArgs.add("--transport");
                    serverArgs.add("QUIC");
                }
                
                // Add --bind for port (Hytale uses --bind to set port)
                serverArgs.add("--bind");
                serverArgs.add(String.valueOf(port));

                // Start process
                String memory = options.getMemory() != null
                        ? options.getMemory()
                        : templateConfig.getMemory();

                ManagedProcess process = processManager.spawnServer(
                        serverId,
                        serverDir,
                        memory,
                        template.getMetadata() != null ? template.getMetadata().getServerJar() : null,
                        templateConfig.getJvmArgs(),
                        serverArgs,
                        environment,
                        true
                );

                instance.markStarting(process);
                instances.put(serverId, instance);

                // Wait for server to be ready
                waitForServerReady(instance);

                LOGGER.info("Spawned dynamic server: {} from template '{}' on port {}",
                        serverId, templateName, port);
                return instance;

            } catch (Exception e) {
                LOGGER.error("Failed to spawn dynamic server from '{}': {}", templateName, e.getMessage());
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    private String generateServerId(ServerTemplate template) {
        String prefix = template.getMetadata() != null
                ? template.getMetadata().getEffectiveServerIdPrefix()
                : template.getName().toLowerCase();
        return prefix + "-" + instanceCounter.incrementAndGet();
    }

    private int allocatePort(int rangeStart, int rangeEnd) {
        for (int port = rangeStart; port <= rangeEnd; port++) {
            if (!allocatedPorts.contains(port)) {
                allocatedPorts.add(port);
                return port;
            }
        }
        return -1;
    }

    private void waitForServerReady(ServerInstance instance) throws InterruptedException {
        int timeout = config.getProcessStartTimeoutSeconds();
        long deadline = System.currentTimeMillis() + (timeout * 1000L);

        while (System.currentTimeMillis() < deadline) {
            if (instance.getProcess() != null && !instance.getProcess().isAlive()) {
                instance.markFailed("Process exited during startup");
                throw new RuntimeException("Server process exited during startup");
            }

            // Check for ready indicator in logs (Hytale server outputs "Listening on")
            if (instance.getProcess() != null) {
                List<String> logs = instance.getProcess().getRecentLogs(50);
                for (String line : logs) {
                    if (line.contains("Server started") ||
                            line.contains("Done") ||
                            line.contains("Ready") ||
                            line.contains("Listening on")) {
                        instance.markRunning();
                        return;
                    }
                }
            }

            Thread.sleep(500);
        }

        // Timeout - assume ready if process is still alive
        if (instance.getProcess() != null && instance.getProcess().isAlive()) {
            LOGGER.warn("Server '{}' startup timeout, assuming ready", instance.getServerId());
            instance.markRunning();
        } else {
            instance.markFailed("Startup timeout");
            throw new RuntimeException("Server startup timeout");
        }
    }

    // ==================== Shutdown ====================

    /**
     * Shutdown a server instance.
     *
     * @param serverId server identifier
     * @param force force immediate shutdown
     * @return completion future
     */
    @Nonnull
    public CompletableFuture<Void> shutdownServer(@Nonnull String serverId, boolean force) {
        return CompletableFuture.runAsync(() -> {
            ServerInstance instance = instances.get(serverId);
            if (instance == null) {
                LOGGER.warn("Server not found for shutdown: {}", serverId);
                return;
            }

            try {
                shutdownInstance(instance, force);
            } catch (Exception e) {
                LOGGER.error("Error shutting down server '{}': {}", serverId, e.getMessage());
            }
        }, asyncExecutor);
    }

    private void shutdownInstance(ServerInstance instance, boolean force) throws IOException {
        String serverId = instance.getServerId();
        LOGGER.info("Shutting down server: {} (force={})", serverId, force);

        instance.markStopping(force ? "Forced shutdown" : "Graceful shutdown");

        // Get timeout from template config
        int timeout = 30;
        if (instance.getTemplate() != null && instance.getTemplate().getMetadata() != null) {
            timeout = instance.getTemplate().getMetadata().getGracefulShutdownTimeout();
        }

        // Kill process
        processManager.killProcess(serverId, !force, timeout);

        // Release port
        allocatedPorts.remove(instance.getPort());

        // Cleanup for dynamic servers
        if (instance.isDynamic() && config.getDynamicSpawning().isAutoCleanup()) {
            Path serverDir = instance.getWorkingDirectory();
            if (Files.exists(serverDir)) {
                LOGGER.debug("Cleaning up dynamic server directory: {}", serverDir);
                deleteDirectory(serverDir);
            }
        }

        instance.markStopped();
        instances.remove(serverId);

        LOGGER.info("Server '{}' shut down successfully", serverId);
    }

    private void deleteDirectory(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // ==================== Health Checks ====================

    private void performHealthChecks() {
        if (shutdown) return;

        for (ServerInstance instance : instances.values()) {
            if (instance.getStatus() == ServerStatus.RUNNING) {
                checkInstanceHealth(instance);
            }
        }
    }

    private void checkInstanceHealth(ServerInstance instance) {
        ManagedProcess process = instance.getProcess();
        if (process == null || !process.isAlive()) {
            LOGGER.warn("Server '{}' process died unexpectedly", instance.getServerId());
            instance.markFailed("Process died");

            // Auto-cleanup for dynamic servers
            if (instance.isDynamic()) {
                asyncExecutor.submit(() -> {
                    try {
                        shutdownInstance(instance, true);
                    } catch (IOException e) {
                        LOGGER.error("Failed to cleanup dead server: {}", e.getMessage());
                    }
                });
            }
        } else {
            // Check heartbeat timeout
            Instant lastHeartbeat = instance.getLastHeartbeat();
            if (lastHeartbeat != null) {
                long elapsed = Instant.now().toEpochMilli() - lastHeartbeat.toEpochMilli();
                if (elapsed > config.getHealthCheckIntervalSeconds() * 3000L) {
                    LOGGER.warn("Server '{}' heartbeat timeout", instance.getServerId());
                    instance.markUnhealthy();
                }
            }
        }
    }

    // ==================== Queries ====================

    /**
     * Get a server instance by ID.
     *
     * @param serverId server identifier
     * @return the instance, or null if not found
     */
    @Nullable
    public ServerInstance getInstance(@Nonnull String serverId) {
        return instances.get(serverId);
    }

    /**
     * Get all server instances.
     *
     * @return unmodifiable collection of instances
     */
    @Nonnull
    public Collection<ServerInstance> getAllInstances() {
        return Collections.unmodifiableCollection(instances.values());
    }

    /**
     * Get instances by type.
     *
     * @param type server type
     * @return list of matching instances
     */
    @Nonnull
    public List<ServerInstance> getInstancesByType(@Nonnull ServerType type) {
        return instances.values().stream()
                .filter(i -> i.getType() == type)
                .toList();
    }

    /**
     * Get instances by template.
     *
     * @param templateName template name
     * @return list of matching instances
     */
    @Nonnull
    public List<ServerInstance> getInstancesByTemplate(@Nonnull String templateName) {
        return instances.values().stream()
                .filter(i -> i.getTemplate() != null &&
                        i.getTemplate().getName().equalsIgnoreCase(templateName))
                .toList();
    }

    /**
     * Get the number of running instances.
     *
     * @return instance count
     */
    public int getInstanceCount() {
        return instances.size();
    }

    /**
     * Get the number of dynamic instances.
     *
     * @return dynamic instance count
     */
    public int getDynamicInstanceCount() {
        return (int) instances.values().stream()
                .filter(ServerInstance::isDynamic)
                .count();
    }

    // ==================== Shutdown ====================

    /**
     * Shutdown the instance manager.
     */
    public void shutdown() {
        shutdown = true;
        LOGGER.info("Shutting down instance manager...");

        // Shutdown all instances
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (String serverId : new ArrayList<>(instances.keySet())) {
            futures.add(shutdownServer(serverId, false));
        }

        // Wait for all shutdowns
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.warn("Timeout waiting for server shutdowns, forcing...");
            for (String serverId : new ArrayList<>(instances.keySet())) {
                processManager.killProcess(serverId, false, 5);
            }
        }

        scheduler.shutdown();
        asyncExecutor.shutdown();

        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
            asyncExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        LOGGER.info("Instance manager shut down");
    }

    /**
     * Options for spawning a dynamic server.
     */
    public static class SpawnOptions {
        private String serverId;
        private int maxPlayers = -1;
        private String memory;
        private boolean worldReset = true;
        private Map<String, Object> metadata;

        public String getServerId() {
            return serverId;
        }

        public SpawnOptions setServerId(String serverId) {
            this.serverId = serverId;
            return this;
        }

        public int getMaxPlayers() {
            return maxPlayers;
        }

        public SpawnOptions setMaxPlayers(int maxPlayers) {
            this.maxPlayers = maxPlayers;
            return this;
        }

        public String getMemory() {
            return memory;
        }

        public SpawnOptions setMemory(String memory) {
            this.memory = memory;
            return this;
        }

        public boolean isWorldReset() {
            return worldReset;
        }

        public SpawnOptions setWorldReset(boolean worldReset) {
            this.worldReset = worldReset;
            return this;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public SpawnOptions setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }
    }
}

package me.internalizable.numdrassl.servermanager.process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/**
 * Manages server process spawning and lifecycle.
 *
 * <p>Handles JVM process creation, monitoring, and termination for
 * Hytale backend servers.</p>
 */
public class ProcessManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessManager.class);

    private final Map<String, ManagedProcess> processes = new ConcurrentHashMap<>();
    private final ScheduledExecutorService monitorExecutor;
    private final Path logsDirectory;
    private final String javaPath;

    private volatile boolean shutdown = false;

    /**
     * Create a process manager.
     *
     * @param logsDirectory directory for server logs
     * @param javaPath path to java executable
     */
    public ProcessManager(@Nonnull Path logsDirectory, @Nonnull String javaPath) {
        this.logsDirectory = Objects.requireNonNull(logsDirectory, "logsDirectory");
        this.javaPath = Objects.requireNonNull(javaPath, "javaPath");
        this.monitorExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "ProcessMonitor");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Initialize the process manager.
     *
     * @throws IOException if initialization fails
     */
    public void initialize() throws IOException {
        Files.createDirectories(logsDirectory);
        Files.createDirectories(logsDirectory.resolve("dynamic"));
        Files.createDirectories(logsDirectory.resolve("static"));
    }

    /**
     * Spawn a Hytale server process.
     *
     * @param serverId unique server identifier
     * @param workingDir working directory for the process
     * @param memory memory allocation (e.g., "2G")
     * @param serverJar path to server JAR (relative to workingDir), defaults to HytaleServer.jar
     * @param jvmArgs additional JVM arguments
     * @param serverArgs Hytale server arguments (--assets, --auth-mode, --transport, etc.)
     * @param environment environment variables
     * @param isDynamic whether this is a dynamic server
     * @return the managed process
     * @throws IOException if spawning fails
     */
    @Nonnull
    public ManagedProcess spawnServer(
            @Nonnull String serverId,
            @Nonnull Path workingDir,
            @Nonnull String memory,
            @Nullable String serverJar,
            @Nonnull List<String> jvmArgs,
            @Nonnull List<String> serverArgs,
            @Nonnull Map<String, String> environment,
            boolean isDynamic) throws IOException {

        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(workingDir, "workingDir");
        Objects.requireNonNull(memory, "memory");

        if (processes.containsKey(serverId)) {
            throw new IllegalStateException("Process already exists for server: " + serverId);
        }

        if (!Files.isDirectory(workingDir)) {
            throw new IOException("Working directory does not exist: " + workingDir);
        }

        // Build Java command for Hytale server
        List<String> command = new ArrayList<>();
        command.add(javaPath);
        command.add("-Xms" + memory);
        command.add("-Xmx" + memory);

        // Default JVM args for Hytale servers
        command.add("-XX:+UseG1GC");
        command.add("-XX:+ParallelRefProcEnabled");
        command.add("-XX:MaxGCPauseMillis=200");

        // Additional JVM args
        command.addAll(jvmArgs);

        // Server JAR (default: HytaleServer.jar)
        String jar = serverJar != null ? serverJar : "HytaleServer.jar";
        if (!Files.exists(workingDir.resolve(jar))) {
            // Try to find any server jar
            jar = findServerJar(workingDir);
            if (jar == null) {
                throw new IOException("No server JAR found in " + workingDir);
            }
        }
        command.add("-jar");
        command.add(jar);

        // Hytale server arguments (--assets, --auth-mode, --transport, etc.)
        if (serverArgs != null && !serverArgs.isEmpty()) {
            command.addAll(serverArgs);
        } else {
            // Default Hytale server arguments
            command.add("--assets");
            command.add("Assets.zip");
            command.add("--auth-mode");
            command.add("insecure");
            command.add("--transport");
            command.add("QUIC");
        }

        LOGGER.info("Spawning server '{}' in {}", serverId, workingDir);
        LOGGER.debug("Command: {}", String.join(" ", command));

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir.toFile());
        builder.redirectErrorStream(true);

        // Set environment
        Map<String, String> env = builder.environment();
        env.put("NUMDRASSL_SERVER_ID", serverId);
        env.put("MEMORY", memory);
        env.putAll(environment);

        // Start process
        Process process = builder.start();

        // Create log file
        String logSubdir = isDynamic ? "dynamic" : "static";
        Path logFile = logsDirectory.resolve(logSubdir).resolve(serverId + ".log");
        
        ManagedProcess managed = new ManagedProcess(serverId, process, workingDir, logFile);
        processes.put(serverId, managed);

        // Start log capture
        startLogCapture(managed);

        // Start monitoring
        scheduleHealthCheck(managed);

        LOGGER.info("Server '{}' started with PID {}", serverId, process.pid());
        return managed;
    }

    /**
     * Spawn a server process (legacy method without server args).
     */
    @Nonnull
    public ManagedProcess spawnServer(
            @Nonnull String serverId,
            @Nonnull Path workingDir,
            @Nonnull String memory,
            @Nullable String serverJar,
            @Nonnull List<String> jvmArgs,
            @Nonnull Map<String, String> environment,
            boolean isDynamic) throws IOException {
        return spawnServer(serverId, workingDir, memory, serverJar, jvmArgs, List.of(), environment, isDynamic);
    }

    /**
     * Spawn a server using a startup script.
     *
     * @param serverId unique server identifier
     * @param workingDir working directory
     * @param startupScript path to startup script
     * @param environment environment variables
     * @param isDynamic whether this is a dynamic server
     * @return the managed process
     * @throws IOException if spawning fails
     */
    @Nonnull
    public ManagedProcess spawnWithScript(
            @Nonnull String serverId,
            @Nonnull Path workingDir,
            @Nonnull Path startupScript,
            @Nonnull Map<String, String> environment,
            boolean isDynamic) throws IOException {

        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(workingDir, "workingDir");
        Objects.requireNonNull(startupScript, "startupScript");

        if (!Files.exists(startupScript)) {
            throw new IOException("Startup script not found: " + startupScript);
        }

        List<String> command = new ArrayList<>();
        command.add("/bin/bash");
        command.add(startupScript.toString());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir.toFile());
        builder.redirectErrorStream(true);

        Map<String, String> env = builder.environment();
        env.put("NUMDRASSL_SERVER_ID", serverId);
        env.putAll(environment);

        Process process = builder.start();

        String logSubdir = isDynamic ? "dynamic" : "static";
        Path logFile = logsDirectory.resolve(logSubdir).resolve(serverId + ".log");

        ManagedProcess managed = new ManagedProcess(serverId, process, workingDir, logFile);
        processes.put(serverId, managed);

        startLogCapture(managed);
        scheduleHealthCheck(managed);

        LOGGER.info("Server '{}' started with script, PID {}", serverId, process.pid());
        return managed;
    }

    @Nullable
    private String findServerJar(Path workingDir) throws IOException {
        try (var stream = Files.newDirectoryStream(workingDir, "*.jar")) {
            for (Path jar : stream) {
                String name = jar.getFileName().toString().toLowerCase();
                if (name.contains("server") || name.contains("hytale")) {
                    return jar.getFileName().toString();
                }
            }
            // Return first JAR if no server jar found
            try (var stream2 = Files.newDirectoryStream(workingDir, "*.jar")) {
                Iterator<Path> it = stream2.iterator();
                if (it.hasNext()) {
                    return it.next().getFileName().toString();
                }
            }
        }
        return null;
    }

    private void startLogCapture(ManagedProcess managed) {
        Thread logThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(managed.getProcess().getInputStream()));
                 BufferedWriter writer = Files.newBufferedWriter(managed.getLogFile())) {

                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line);
                    writer.newLine();
                    writer.flush();
                    managed.addLogLine(line);
                }
            } catch (IOException e) {
                if (!shutdown) {
                    LOGGER.error("Error capturing logs for '{}': {}", managed.getServerId(), e.getMessage());
                }
            }
        }, "LogCapture-" + managed.getServerId());
        logThread.setDaemon(true);
        logThread.start();
    }

    private void scheduleHealthCheck(ManagedProcess managed) {
        monitorExecutor.scheduleAtFixedRate(() -> {
            if (!managed.getProcess().isAlive()) {
                int exitCode = managed.getProcess().exitValue();
                LOGGER.warn("Server '{}' exited with code {}", managed.getServerId(), exitCode);
                managed.setExitCode(exitCode);
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    /**
     * Kill a server process.
     *
     * @param serverId server identifier
     * @param graceful whether to attempt graceful shutdown first
     * @param timeoutSeconds timeout for graceful shutdown
     * @return true if process was killed
     */
    public boolean killProcess(@Nonnull String serverId, boolean graceful, int timeoutSeconds) {
        Objects.requireNonNull(serverId, "serverId");

        ManagedProcess managed = processes.get(serverId);
        if (managed == null) {
            LOGGER.warn("No process found for server: {}", serverId);
            return false;
        }

        Process process = managed.getProcess();
        if (!process.isAlive()) {
            processes.remove(serverId);
            return true;
        }

        if (graceful) {
            LOGGER.info("Requesting graceful shutdown for '{}'", serverId);
            process.destroy();

            try {
                boolean exited = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                if (exited) {
                    LOGGER.info("Server '{}' shut down gracefully", serverId);
                    processes.remove(serverId);
                    return true;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            LOGGER.warn("Server '{}' did not shut down gracefully, forcing...", serverId);
        }

        process.destroyForcibly();
        try {
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        processes.remove(serverId);
        LOGGER.info("Server '{}' forcibly terminated", serverId);
        return true;
    }

    /**
     * Check if a process is alive.
     *
     * @param serverId server identifier
     * @return true if process is running
     */
    public boolean isProcessAlive(@Nonnull String serverId) {
        ManagedProcess managed = processes.get(serverId);
        return managed != null && managed.getProcess().isAlive();
    }

    /**
     * Get a managed process.
     *
     * @param serverId server identifier
     * @return the managed process, or null if not found
     */
    @Nullable
    public ManagedProcess getProcess(@Nonnull String serverId) {
        return processes.get(serverId);
    }

    /**
     * Get all managed processes.
     *
     * @return unmodifiable collection of processes
     */
    @Nonnull
    public Collection<ManagedProcess> getAllProcesses() {
        return Collections.unmodifiableCollection(processes.values());
    }

    /**
     * Get process metrics.
     *
     * @param serverId server identifier
     * @return metrics, or null if process not found
     */
    @Nullable
    public ProcessMetrics getProcessMetrics(@Nonnull String serverId) {
        ManagedProcess managed = processes.get(serverId);
        if (managed == null || !managed.getProcess().isAlive()) {
            return null;
        }

        Process process = managed.getProcess();
        ProcessHandle handle = process.toHandle();

        return new ProcessMetrics(
                handle.pid(),
                managed.getStartTime(),
                System.currentTimeMillis() - managed.getStartTime(),
                handle.info().totalCpuDuration().map(d -> d.toMillis()).orElse(-1L),
                -1L // Memory not easily available without JMX
        );
    }

    /**
     * Read recent log lines for a server.
     *
     * @param serverId server identifier
     * @param lines number of lines to read
     * @return list of log lines
     */
    @Nonnull
    public List<String> getRecentLogs(@Nonnull String serverId, int lines) {
        ManagedProcess managed = processes.get(serverId);
        if (managed == null) {
            return List.of();
        }
        return managed.getRecentLogs(lines);
    }

    /**
     * Shutdown the process manager.
     */
    public void shutdown() {
        shutdown = true;
        LOGGER.info("Shutting down process manager...");

        // Kill all processes
        for (String serverId : new ArrayList<>(processes.keySet())) {
            killProcess(serverId, true, 10);
        }

        monitorExecutor.shutdown();
        try {
            if (!monitorExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                monitorExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            monitorExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        LOGGER.info("Process manager shut down");
    }

    /**
     * Process metrics.
     */
    public record ProcessMetrics(
            long pid,
            long startTime,
            long uptimeMillis,
            long cpuTimeMillis,
            long memoryBytes
    ) {}
}

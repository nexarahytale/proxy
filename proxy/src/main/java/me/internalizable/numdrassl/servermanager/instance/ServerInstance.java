package me.internalizable.numdrassl.servermanager.instance;

import me.internalizable.numdrassl.servermanager.process.ManagedProcess;
import me.internalizable.numdrassl.servermanager.registry.ServerType;
import me.internalizable.numdrassl.servermanager.template.ServerTemplate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a running server instance (static or dynamic).
 *
 * <p>Tracks the server's lifecycle, process handle, player connections,
 * and health status.</p>
 *
 * <h2>Lifecycle States</h2>
 * <pre>
 * CREATED → STARTING → RUNNING → STOPPING → STOPPED
 *                ↓         ↓
 *              FAILED   UNHEALTHY
 * </pre>
 */
public class ServerInstance {

    private final String serverId;
    private final ServerType type;
    private final Path workingDirectory;
    private final int port;
    private final int maxPlayers;
    private final Instant createdAt;
    private final Map<String, Object> metadata = new ConcurrentHashMap<>();

    @Nullable
    private final ServerTemplate template;

    private volatile ServerStatus status = ServerStatus.CREATED;
    private volatile ManagedProcess process;
    private volatile Instant lastHeartbeat;
    private volatile Instant startedAt;
    private volatile Instant stoppedAt;
    private volatile String stopReason;

    private final Set<UUID> connectedPlayers = ConcurrentHashMap.newKeySet();

    /**
     * Create a server instance.
     *
     * @param serverId unique server identifier
     * @param type server type (STATIC or DYNAMIC)
     * @param workingDirectory server working directory
     * @param port server port
     * @param maxPlayers maximum player capacity
     * @param template source template (null for static servers)
     */
    public ServerInstance(
            @Nonnull String serverId,
            @Nonnull ServerType type,
            @Nonnull Path workingDirectory,
            int port,
            int maxPlayers,
            @Nullable ServerTemplate template) {
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.type = Objects.requireNonNull(type, "type");
        this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory");
        this.port = port;
        this.maxPlayers = maxPlayers;
        this.template = template;
        this.createdAt = Instant.now();
    }

    // ==================== Lifecycle ====================

    /**
     * Mark the server as starting.
     *
     * @param process the managed process
     */
    public void markStarting(@Nonnull ManagedProcess process) {
        this.process = Objects.requireNonNull(process, "process");
        this.status = ServerStatus.STARTING;
        this.startedAt = Instant.now();
    }

    /**
     * Mark the server as running (ready to accept players).
     */
    public void markRunning() {
        this.status = ServerStatus.RUNNING;
        this.lastHeartbeat = Instant.now();
    }

    /**
     * Mark the server as stopping.
     *
     * @param reason reason for shutdown
     */
    public void markStopping(@Nullable String reason) {
        this.status = ServerStatus.STOPPING;
        this.stopReason = reason;
    }

    /**
     * Mark the server as stopped.
     */
    public void markStopped() {
        this.status = ServerStatus.STOPPED;
        this.stoppedAt = Instant.now();
    }

    /**
     * Mark the server as failed.
     *
     * @param reason failure reason
     */
    public void markFailed(@Nullable String reason) {
        this.status = ServerStatus.FAILED;
        this.stopReason = reason;
        this.stoppedAt = Instant.now();
    }

    /**
     * Mark the server as unhealthy.
     */
    public void markUnhealthy() {
        this.status = ServerStatus.UNHEALTHY;
    }

    /**
     * Update the heartbeat timestamp.
     */
    public void updateHeartbeat() {
        this.lastHeartbeat = Instant.now();
        if (status == ServerStatus.UNHEALTHY) {
            status = ServerStatus.RUNNING;
        }
    }

    // ==================== Player Management ====================

    /**
     * Add a connected player.
     *
     * @param playerUuid player UUID
     */
    public void addPlayer(@Nonnull UUID playerUuid) {
        connectedPlayers.add(playerUuid);
    }

    /**
     * Remove a connected player.
     *
     * @param playerUuid player UUID
     */
    public void removePlayer(@Nonnull UUID playerUuid) {
        connectedPlayers.remove(playerUuid);
    }

    /**
     * Get the number of connected players.
     *
     * @return player count
     */
    public int getPlayerCount() {
        return connectedPlayers.size();
    }

    /**
     * Get all connected player UUIDs.
     *
     * @return unmodifiable set of player UUIDs
     */
    @Nonnull
    public Set<UUID> getConnectedPlayers() {
        return Collections.unmodifiableSet(connectedPlayers);
    }

    /**
     * Check if a player is connected.
     *
     * @param playerUuid player UUID
     * @return true if connected
     */
    public boolean hasPlayer(@Nonnull UUID playerUuid) {
        return connectedPlayers.contains(playerUuid);
    }

    /**
     * Check if the server is full.
     *
     * @return true if at max capacity
     */
    public boolean isFull() {
        return connectedPlayers.size() >= maxPlayers;
    }

    // ==================== Metadata ====================

    /**
     * Set a metadata value.
     *
     * @param key metadata key
     * @param value metadata value
     */
    public void setMetadata(@Nonnull String key, @Nullable Object value) {
        if (value == null) {
            metadata.remove(key);
        } else {
            metadata.put(key, value);
        }
    }

    /**
     * Get a metadata value.
     *
     * @param key metadata key
     * @return metadata value, or null if not set
     */
    @Nullable
    public Object getMetadata(@Nonnull String key) {
        return metadata.get(key);
    }

    /**
     * Get a typed metadata value.
     *
     * @param key metadata key
     * @param type expected type
     * @param <T> type parameter
     * @return metadata value, or null if not set or wrong type
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(@Nonnull String key, @Nonnull Class<T> type) {
        Object value = metadata.get(key);
        if (type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * Get all metadata.
     *
     * @return unmodifiable map of metadata
     */
    @Nonnull
    public Map<String, Object> getAllMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    // ==================== Status Checks ====================

    /**
     * Check if the server is healthy.
     *
     * @return true if running and process is alive
     */
    public boolean isHealthy() {
        if (status != ServerStatus.RUNNING && status != ServerStatus.STARTING) {
            return false;
        }
        return process != null && process.isAlive();
    }

    /**
     * Check if the server is accepting players.
     *
     * @return true if running and not full
     */
    public boolean isAcceptingPlayers() {
        return status == ServerStatus.RUNNING && !isFull();
    }

    /**
     * Check if this is a dynamic server.
     *
     * @return true if dynamic
     */
    public boolean isDynamic() {
        return type == ServerType.DYNAMIC;
    }

    /**
     * Check if this is a static server.
     *
     * @return true if static
     */
    public boolean isStatic() {
        return type == ServerType.STATIC;
    }

    // ==================== Getters ====================

    /**
     * Get the server identifier.
     *
     * @return server ID
     */
    @Nonnull
    public String getServerId() {
        return serverId;
    }

    /**
     * Get the server type.
     *
     * @return server type
     */
    @Nonnull
    public ServerType getType() {
        return type;
    }

    /**
     * Get the working directory.
     *
     * @return working directory path
     */
    @Nonnull
    public Path getWorkingDirectory() {
        return workingDirectory;
    }

    /**
     * Get the server port.
     *
     * @return port number
     */
    public int getPort() {
        return port;
    }

    /**
     * Get the maximum player capacity.
     *
     * @return max players
     */
    public int getMaxPlayers() {
        return maxPlayers;
    }

    /**
     * Get the source template (for dynamic servers).
     *
     * @return template, or null for static servers
     */
    @Nullable
    public ServerTemplate getTemplate() {
        return template;
    }

    /**
     * Get the current status.
     *
     * @return server status
     */
    @Nonnull
    public ServerStatus getStatus() {
        return status;
    }

    /**
     * Get the managed process.
     *
     * @return process, or null if not started
     */
    @Nullable
    public ManagedProcess getProcess() {
        return process;
    }

    /**
     * Get the creation timestamp.
     *
     * @return creation time
     */
    @Nonnull
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Get the start timestamp.
     *
     * @return start time, or null if not started
     */
    @Nullable
    public Instant getStartedAt() {
        return startedAt;
    }

    /**
     * Get the stop timestamp.
     *
     * @return stop time, or null if not stopped
     */
    @Nullable
    public Instant getStoppedAt() {
        return stoppedAt;
    }

    /**
     * Get the last heartbeat timestamp.
     *
     * @return last heartbeat, or null if never received
     */
    @Nullable
    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    /**
     * Get the stop/failure reason.
     *
     * @return reason, or null if not stopped/failed
     */
    @Nullable
    public String getStopReason() {
        return stopReason;
    }

    /**
     * Get the uptime in milliseconds.
     *
     * @return uptime, or 0 if not started
     */
    public long getUptimeMillis() {
        if (startedAt == null) {
            return 0;
        }
        Instant end = stoppedAt != null ? stoppedAt : Instant.now();
        return end.toEpochMilli() - startedAt.toEpochMilli();
    }

    @Override
    public String toString() {
        return "ServerInstance{" +
                "serverId='" + serverId + '\'' +
                ", type=" + type +
                ", status=" + status +
                ", port=" + port +
                ", players=" + getPlayerCount() + "/" + maxPlayers +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServerInstance that = (ServerInstance) o;
        return serverId.equals(that.serverId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serverId);
    }
}

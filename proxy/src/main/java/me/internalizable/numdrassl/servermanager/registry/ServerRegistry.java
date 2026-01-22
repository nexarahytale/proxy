package me.internalizable.numdrassl.servermanager.registry;

import me.internalizable.numdrassl.servermanager.instance.ServerInstance;
import me.internalizable.numdrassl.servermanager.instance.ServerStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * In-memory registry of all managed servers.
 *
 * <p>Provides fast lookup and filtering of server instances.
 * Static servers are persisted to configuration, while dynamic
 * servers are ephemeral and lost on proxy restart.</p>
 */
public class ServerRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerRegistry.class);

    private final Map<String, ServerInstance> servers = new ConcurrentHashMap<>();
    private final Map<Integer, String> portToServer = new ConcurrentHashMap<>();

    /**
     * Register a server instance.
     *
     * @param instance the server instance
     * @throws IllegalStateException if server ID or port already registered
     */
    public void register(@Nonnull ServerInstance instance) {
        Objects.requireNonNull(instance, "instance");

        String serverId = instance.getServerId();
        int port = instance.getPort();

        if (servers.containsKey(serverId)) {
            throw new IllegalStateException("Server already registered: " + serverId);
        }

        if (portToServer.containsKey(port)) {
            throw new IllegalStateException("Port already in use: " + port +
                    " (by " + portToServer.get(port) + ")");
        }

        servers.put(serverId, instance);
        portToServer.put(port, serverId);

        LOGGER.debug("Registered server: {} on port {}", serverId, port);
    }

    /**
     * Unregister a server instance.
     *
     * @param serverId server identifier
     * @return the removed instance, or null if not found
     */
    @Nullable
    public ServerInstance unregister(@Nonnull String serverId) {
        Objects.requireNonNull(serverId, "serverId");

        ServerInstance instance = servers.remove(serverId);
        if (instance != null) {
            portToServer.remove(instance.getPort());
            LOGGER.debug("Unregistered server: {}", serverId);
        }
        return instance;
    }

    /**
     * Get a server by ID.
     *
     * @param serverId server identifier
     * @return the server instance, or null if not found
     */
    @Nullable
    public ServerInstance getServer(@Nonnull String serverId) {
        Objects.requireNonNull(serverId, "serverId");
        return servers.get(serverId);
    }

    /**
     * Get a server by port.
     *
     * @param port server port
     * @return the server instance, or null if not found
     */
    @Nullable
    public ServerInstance getServerByPort(int port) {
        String serverId = portToServer.get(port);
        return serverId != null ? servers.get(serverId) : null;
    }

    /**
     * Check if a server is registered.
     *
     * @param serverId server identifier
     * @return true if registered
     */
    public boolean hasServer(@Nonnull String serverId) {
        return servers.containsKey(serverId);
    }

    /**
     * Check if a port is in use.
     *
     * @param port port number
     * @return true if port is allocated
     */
    public boolean isPortInUse(int port) {
        return portToServer.containsKey(port);
    }

    /**
     * Get all registered servers.
     *
     * @return unmodifiable collection of servers
     */
    @Nonnull
    public Collection<ServerInstance> getAllServers() {
        return Collections.unmodifiableCollection(servers.values());
    }

    /**
     * Get all server IDs.
     *
     * @return unmodifiable set of server IDs
     */
    @Nonnull
    public Set<String> getServerIds() {
        return Collections.unmodifiableSet(servers.keySet());
    }

    /**
     * Get servers by type.
     *
     * @param type server type filter
     * @return list of matching servers
     */
    @Nonnull
    public List<ServerInstance> getServersByType(@Nonnull ServerType type) {
        Objects.requireNonNull(type, "type");
        return servers.values().stream()
                .filter(s -> s.getType() == type)
                .collect(Collectors.toList());
    }

    /**
     * Get servers by status.
     *
     * @param status server status filter
     * @return list of matching servers
     */
    @Nonnull
    public List<ServerInstance> getServersByStatus(@Nonnull ServerStatus status) {
        Objects.requireNonNull(status, "status");
        return servers.values().stream()
                .filter(s -> s.getStatus() == status)
                .collect(Collectors.toList());
    }

    /**
     * Get servers matching a predicate.
     *
     * @param predicate filter predicate
     * @return list of matching servers
     */
    @Nonnull
    public List<ServerInstance> getServers(@Nonnull Predicate<ServerInstance> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        return servers.values().stream()
                .filter(predicate)
                .collect(Collectors.toList());
    }

    /**
     * Get running servers that can accept players.
     *
     * @return list of available servers
     */
    @Nonnull
    public List<ServerInstance> getAvailableServers() {
        return servers.values().stream()
                .filter(ServerInstance::isAcceptingPlayers)
                .collect(Collectors.toList());
    }

    /**
     * Get static servers.
     *
     * @return list of static servers
     */
    @Nonnull
    public List<ServerInstance> getStaticServers() {
        return getServersByType(ServerType.STATIC);
    }

    /**
     * Get dynamic servers.
     *
     * @return list of dynamic servers
     */
    @Nonnull
    public List<ServerInstance> getDynamicServers() {
        return getServersByType(ServerType.DYNAMIC);
    }

    /**
     * Find a server with available capacity.
     *
     * @param templateName optional template name filter
     * @return a server with space, or null if none available
     */
    @Nullable
    public ServerInstance findAvailableServer(@Nullable String templateName) {
        return servers.values().stream()
                .filter(ServerInstance::isAcceptingPlayers)
                .filter(s -> templateName == null ||
                        (s.getTemplate() != null &&
                                s.getTemplate().getName().equalsIgnoreCase(templateName)))
                .min(Comparator.comparingInt(ServerInstance::getPlayerCount))
                .orElse(null);
    }

    /**
     * Get the total number of registered servers.
     *
     * @return server count
     */
    public int getServerCount() {
        return servers.size();
    }

    /**
     * Get the total number of players across all servers.
     *
     * @return total player count
     */
    public int getTotalPlayerCount() {
        return servers.values().stream()
                .mapToInt(ServerInstance::getPlayerCount)
                .sum();
    }

    /**
     * Get registry statistics.
     *
     * @return statistics object
     */
    @Nonnull
    public RegistryStats getStats() {
        int total = servers.size();
        int staticCount = 0;
        int dynamicCount = 0;
        int running = 0;
        int players = 0;

        for (ServerInstance server : servers.values()) {
            if (server.isStatic()) staticCount++;
            else dynamicCount++;

            if (server.getStatus() == ServerStatus.RUNNING) running++;
            players += server.getPlayerCount();
        }

        return new RegistryStats(total, staticCount, dynamicCount, running, players);
    }

    /**
     * Clear all registrations.
     */
    public void clear() {
        servers.clear();
        portToServer.clear();
        LOGGER.debug("Registry cleared");
    }

    /**
     * Registry statistics.
     */
    public record RegistryStats(
            int totalServers,
            int staticServers,
            int dynamicServers,
            int runningServers,
            int totalPlayers
    ) {}
}

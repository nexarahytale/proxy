package me.internalizable.numdrassl.api.servermanager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * API for CloudNet-style server management.
 *
 * <p>Provides methods for spawning dynamic servers, managing templates,
 * and querying server status.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * ServerManagerAPI api = Numdrassl.getProxy().getServerManager();
 * 
 * // Spawn a dynamic server
 * api.spawnServer("bedwars", SpawnOptions.builder()
 *     .maxPlayers(8)
 *     .metadata("gameMode", "ranked")
 *     .build())
 *     .thenAccept(info -> {
 *         System.out.println("Server spawned: " + info.getServerId());
 *     });
 * 
 * // Shutdown when done
 * api.shutdownServer("bedwars-1");
 * }</pre>
 */
public interface ServerManagerAPI {

    /**
     * Spawn a dynamic server from a template.
     *
     * @param templateName template name
     * @param options spawn options
     * @return future completing with server info
     */
    @Nonnull
    CompletableFuture<ServerInfo> spawnServer(@Nonnull String templateName, @Nonnull SpawnOptions options);

    /**
     * Spawn a dynamic server with default options.
     *
     * @param templateName template name
     * @return future completing with server info
     */
    @Nonnull
    default CompletableFuture<ServerInfo> spawnServer(@Nonnull String templateName) {
        return spawnServer(templateName, SpawnOptions.defaults());
    }

    /**
     * Shutdown a server.
     *
     * @param serverId server identifier
     * @return future completing when shutdown is done
     */
    @Nonnull
    CompletableFuture<Void> shutdownServer(@Nonnull String serverId);

    /**
     * Shutdown a server with force option.
     *
     * @param serverId server identifier
     * @param force force immediate shutdown
     * @return future completing when shutdown is done
     */
    @Nonnull
    CompletableFuture<Void> shutdownServer(@Nonnull String serverId, boolean force);

    /**
     * Get information about a server.
     *
     * @param serverId server identifier
     * @return server info, or null if not found
     */
    @Nullable
    ServerInfo getServer(@Nonnull String serverId);

    /**
     * Get all servers.
     *
     * @return collection of server info
     */
    @Nonnull
    Collection<ServerInfo> getAllServers();

    /**
     * Get servers by type.
     *
     * @param type server type (STATIC or DYNAMIC)
     * @return collection of matching servers
     */
    @Nonnull
    Collection<ServerInfo> getServersByType(@Nonnull String type);

    /**
     * Get servers by template.
     *
     * @param templateName template name
     * @return collection of servers spawned from template
     */
    @Nonnull
    Collection<ServerInfo> getServersByTemplate(@Nonnull String templateName);

    /**
     * Get all available templates.
     *
     * @return collection of template names
     */
    @Nonnull
    Collection<String> getTemplates();

    /**
     * Check if a template exists.
     *
     * @param templateName template name
     * @return true if template exists
     */
    boolean hasTemplate(@Nonnull String templateName);

    /**
     * Find an available server with capacity.
     *
     * @param templateName optional template filter
     * @return server info, or null if none available
     */
    @Nullable
    ServerInfo findAvailableServer(@Nullable String templateName);

    /**
     * Get server manager statistics.
     *
     * @return statistics
     */
    @Nonnull
    ServerManagerStats getStats();

    /**
     * Server information.
     */
    interface ServerInfo {
        /**
         * Get the server identifier.
         */
        @Nonnull
        String getServerId();

        /**
         * Get the server type (STATIC or DYNAMIC).
         */
        @Nonnull
        String getType();

        /**
         * Get the server status.
         */
        @Nonnull
        String getStatus();

        /**
         * Get the server port.
         */
        int getPort();

        /**
         * Get the current player count.
         */
        int getPlayerCount();

        /**
         * Get the maximum player capacity.
         */
        int getMaxPlayers();

        /**
         * Get the template name (for dynamic servers).
         */
        @Nullable
        String getTemplateName();

        /**
         * Get server metadata.
         */
        @Nonnull
        Map<String, Object> getMetadata();

        /**
         * Check if the server is accepting players.
         */
        boolean isAcceptingPlayers();

        /**
         * Get the server uptime in milliseconds.
         */
        long getUptimeMillis();
    }

    /**
     * Options for spawning a server.
     */
    interface SpawnOptions {
        /**
         * Get the custom server ID (null for auto-generated).
         */
        @Nullable
        String getServerId();

        /**
         * Get the max players override (-1 for template default).
         */
        int getMaxPlayers();

        /**
         * Get the memory override (null for template default).
         */
        @Nullable
        String getMemory();

        /**
         * Get custom metadata.
         */
        @Nonnull
        Map<String, Object> getMetadata();

        /**
         * Create default spawn options.
         */
        @Nonnull
        static SpawnOptions defaults() {
            return builder().build();
        }

        /**
         * Create a builder for spawn options.
         */
        @Nonnull
        static Builder builder() {
            return new SpawnOptionsBuilder();
        }

        /**
         * Builder for spawn options.
         */
        interface Builder {
            Builder serverId(@Nullable String serverId);
            Builder maxPlayers(int maxPlayers);
            Builder memory(@Nullable String memory);
            Builder metadata(@Nonnull String key, @Nonnull Object value);
            Builder metadata(@Nonnull Map<String, Object> metadata);
            SpawnOptions build();
        }
    }

    /**
     * Server manager statistics.
     */
    interface ServerManagerStats {
        int getTotalServers();
        int getStaticServers();
        int getDynamicServers();
        int getRunningServers();
        int getTotalPlayers();
        int getAvailableTemplates();
    }
}

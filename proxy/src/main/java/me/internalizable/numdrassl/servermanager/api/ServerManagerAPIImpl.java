package me.internalizable.numdrassl.servermanager.api;

import me.internalizable.numdrassl.api.servermanager.ServerManagerAPI;
import me.internalizable.numdrassl.servermanager.ServerManager;
import me.internalizable.numdrassl.servermanager.instance.InstanceManager;
import me.internalizable.numdrassl.servermanager.instance.ServerInstance;
import me.internalizable.numdrassl.servermanager.registry.ServerRegistry;
import me.internalizable.numdrassl.servermanager.template.ServerTemplate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Implementation of the ServerManagerAPI for plugin access.
 */
public class ServerManagerAPIImpl implements ServerManagerAPI {

    private final ServerManager serverManager;

    public ServerManagerAPIImpl(@Nonnull ServerManager serverManager) {
        this.serverManager = Objects.requireNonNull(serverManager, "serverManager");
    }

    @Override
    @Nonnull
    public CompletableFuture<ServerInfo> spawnServer(@Nonnull String templateName, @Nonnull SpawnOptions options) {
        Objects.requireNonNull(templateName, "templateName");
        Objects.requireNonNull(options, "options");

        InstanceManager.SpawnOptions internalOptions = new InstanceManager.SpawnOptions()
                .setServerId(options.getServerId())
                .setMaxPlayers(options.getMaxPlayers())
                .setMemory(options.getMemory())
                .setMetadata(new HashMap<>(options.getMetadata()));

        return serverManager.spawnDynamicServer(templateName, internalOptions)
                .thenApply(this::toServerInfo);
    }

    @Override
    @Nonnull
    public CompletableFuture<Void> shutdownServer(@Nonnull String serverId) {
        return shutdownServer(serverId, false);
    }

    @Override
    @Nonnull
    public CompletableFuture<Void> shutdownServer(@Nonnull String serverId, boolean force) {
        Objects.requireNonNull(serverId, "serverId");
        return serverManager.shutdownServer(serverId, force);
    }

    @Override
    @Nullable
    public ServerInfo getServer(@Nonnull String serverId) {
        Objects.requireNonNull(serverId, "serverId");
        ServerInstance instance = serverManager.getServer(serverId);
        return instance != null ? toServerInfo(instance) : null;
    }

    @Override
    @Nonnull
    public Collection<ServerInfo> getAllServers() {
        return serverManager.getAllServers().stream()
                .map(this::toServerInfo)
                .collect(Collectors.toList());
    }

    @Override
    @Nonnull
    public Collection<ServerInfo> getServersByType(@Nonnull String type) {
        Objects.requireNonNull(type, "type");
        return serverManager.getAllServers().stream()
                .filter(s -> s.getType().name().equalsIgnoreCase(type))
                .map(this::toServerInfo)
                .collect(Collectors.toList());
    }

    @Override
    @Nonnull
    public Collection<ServerInfo> getServersByTemplate(@Nonnull String templateName) {
        Objects.requireNonNull(templateName, "templateName");
        return serverManager.getAllServers().stream()
                .filter(s -> s.getTemplate() != null &&
                        s.getTemplate().getName().equalsIgnoreCase(templateName))
                .map(this::toServerInfo)
                .collect(Collectors.toList());
    }

    @Override
    @Nonnull
    public Collection<String> getTemplates() {
        return serverManager.getAllTemplates().stream()
                .map(ServerTemplate::getName)
                .collect(Collectors.toList());
    }

    @Override
    public boolean hasTemplate(@Nonnull String templateName) {
        Objects.requireNonNull(templateName, "templateName");
        return serverManager.getTemplate(templateName) != null;
    }

    @Override
    @Nullable
    public ServerInfo findAvailableServer(@Nullable String templateName) {
        ServerInstance instance = serverManager.getRegistry().findAvailableServer(templateName);
        return instance != null ? toServerInfo(instance) : null;
    }

    @Override
    @Nonnull
    public ServerManagerStats getStats() {
        ServerRegistry.RegistryStats stats = serverManager.getStats();
        int templates = serverManager.getAllTemplates().size();

        return new ServerManagerStatsImpl(
                stats.totalServers(),
                stats.staticServers(),
                stats.dynamicServers(),
                stats.runningServers(),
                stats.totalPlayers(),
                templates
        );
    }

    private ServerInfo toServerInfo(ServerInstance instance) {
        return new ServerInfoImpl(
                instance.getServerId(),
                instance.getType().name(),
                instance.getStatus().name(),
                instance.getPort(),
                instance.getPlayerCount(),
                instance.getMaxPlayers(),
                instance.getTemplate() != null ? instance.getTemplate().getName() : null,
                new HashMap<>(instance.getAllMetadata()),
                instance.isAcceptingPlayers(),
                instance.getUptimeMillis()
        );
    }

    private record ServerInfoImpl(
            String serverId,
            String type,
            String status,
            int port,
            int playerCount,
            int maxPlayers,
            String templateName,
            Map<String, Object> metadata,
            boolean acceptingPlayers,
            long uptimeMillis
    ) implements ServerInfo {

        @Override
        @Nonnull
        public String getServerId() {
            return serverId;
        }

        @Override
        @Nonnull
        public String getType() {
            return type;
        }

        @Override
        @Nonnull
        public String getStatus() {
            return status;
        }

        @Override
        public int getPort() {
            return port;
        }

        @Override
        public int getPlayerCount() {
            return playerCount;
        }

        @Override
        public int getMaxPlayers() {
            return maxPlayers;
        }

        @Override
        @Nullable
        public String getTemplateName() {
            return templateName;
        }

        @Override
        @Nonnull
        public Map<String, Object> getMetadata() {
            return Collections.unmodifiableMap(metadata);
        }

        @Override
        public boolean isAcceptingPlayers() {
            return acceptingPlayers;
        }

        @Override
        public long getUptimeMillis() {
            return uptimeMillis;
        }
    }

    private record ServerManagerStatsImpl(
            int totalServers,
            int staticServers,
            int dynamicServers,
            int runningServers,
            int totalPlayers,
            int availableTemplates
    ) implements ServerManagerStats {

        @Override
        public int getTotalServers() {
            return totalServers;
        }

        @Override
        public int getStaticServers() {
            return staticServers;
        }

        @Override
        public int getDynamicServers() {
            return dynamicServers;
        }

        @Override
        public int getRunningServers() {
            return runningServers;
        }

        @Override
        public int getTotalPlayers() {
            return totalPlayers;
        }

        @Override
        public int getAvailableTemplates() {
            return availableTemplates;
        }
    }
}

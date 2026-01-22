package me.internalizable.numdrassl.api.servermanager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Builder implementation for spawn options.
 */
public class SpawnOptionsBuilder implements ServerManagerAPI.SpawnOptions.Builder {

    private String serverId;
    private int maxPlayers = -1;
    private String memory;
    private final Map<String, Object> metadata = new HashMap<>();

    @Override
    public ServerManagerAPI.SpawnOptions.Builder serverId(@Nullable String serverId) {
        this.serverId = serverId;
        return this;
    }

    @Override
    public ServerManagerAPI.SpawnOptions.Builder maxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
        return this;
    }

    @Override
    public ServerManagerAPI.SpawnOptions.Builder memory(@Nullable String memory) {
        this.memory = memory;
        return this;
    }

    @Override
    public ServerManagerAPI.SpawnOptions.Builder metadata(@Nonnull String key, @Nonnull Object value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        this.metadata.put(key, value);
        return this;
    }

    @Override
    public ServerManagerAPI.SpawnOptions.Builder metadata(@Nonnull Map<String, Object> metadata) {
        Objects.requireNonNull(metadata, "metadata");
        this.metadata.putAll(metadata);
        return this;
    }

    @Override
    public ServerManagerAPI.SpawnOptions build() {
        return new SpawnOptionsImpl(serverId, maxPlayers, memory, new HashMap<>(metadata));
    }

    private record SpawnOptionsImpl(
            String serverId,
            int maxPlayers,
            String memory,
            Map<String, Object> metadata
    ) implements ServerManagerAPI.SpawnOptions {

        @Override
        @Nullable
        public String getServerId() {
            return serverId;
        }

        @Override
        public int getMaxPlayers() {
            return maxPlayers;
        }

        @Override
        @Nullable
        public String getMemory() {
            return memory;
        }

        @Override
        @Nonnull
        public Map<String, Object> getMetadata() {
            return Collections.unmodifiableMap(metadata);
        }
    }
}

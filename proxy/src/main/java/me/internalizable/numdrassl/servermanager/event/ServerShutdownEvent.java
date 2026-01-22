package me.internalizable.numdrassl.servermanager.event;

import me.internalizable.numdrassl.servermanager.instance.ServerInstance;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Event fired when a server instance is shutting down.
 *
 * <p>This event is fired before the server process is terminated,
 * allowing plugins to perform cleanup or player transfers.</p>
 */
public class ServerShutdownEvent {

    private final ServerInstance instance;
    private final ShutdownReason reason;
    private final boolean forced;

    /**
     * Create a server shutdown event.
     *
     * @param instance the server instance
     * @param reason shutdown reason
     * @param forced whether this is a forced shutdown
     */
    public ServerShutdownEvent(
            @Nonnull ServerInstance instance,
            @Nonnull ShutdownReason reason,
            boolean forced) {
        this.instance = Objects.requireNonNull(instance, "instance");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.forced = forced;
    }

    /**
     * Get the server instance.
     *
     * @return the server instance
     */
    @Nonnull
    public ServerInstance getInstance() {
        return instance;
    }

    /**
     * Get the server ID.
     *
     * @return server identifier
     */
    @Nonnull
    public String getServerId() {
        return instance.getServerId();
    }

    /**
     * Get the shutdown reason.
     *
     * @return shutdown reason
     */
    @Nonnull
    public ShutdownReason getReason() {
        return reason;
    }

    /**
     * Check if this is a forced shutdown.
     *
     * @return true if forced
     */
    public boolean isForced() {
        return forced;
    }

    /**
     * Check if this is a dynamic server.
     *
     * @return true if dynamic
     */
    public boolean isDynamic() {
        return instance.isDynamic();
    }

    /**
     * Reasons for server shutdown.
     */
    public enum ShutdownReason {
        /**
         * Requested by admin command.
         */
        ADMIN_REQUEST,

        /**
         * Game/match ended normally.
         */
        GAME_ENDED,

        /**
         * Server process crashed.
         */
        PROCESS_CRASHED,

        /**
         * Health check failed.
         */
        HEALTH_CHECK_FAILED,

        /**
         * Proxy is shutting down.
         */
        PROXY_SHUTDOWN,

        /**
         * Auto-cleanup after timeout.
         */
        AUTO_CLEANUP,

        /**
         * Unknown or unspecified reason.
         */
        UNKNOWN
    }
}

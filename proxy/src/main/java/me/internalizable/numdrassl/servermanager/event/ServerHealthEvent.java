package me.internalizable.numdrassl.servermanager.event;

import me.internalizable.numdrassl.servermanager.instance.ServerInstance;
import me.internalizable.numdrassl.servermanager.instance.ServerStatus;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Event fired when a server's health status changes.
 */
public class ServerHealthEvent {

    private final ServerInstance instance;
    private final ServerStatus previousStatus;
    private final ServerStatus newStatus;
    private final String message;

    /**
     * Create a server health event.
     *
     * @param instance the server instance
     * @param previousStatus previous status
     * @param newStatus new status
     * @param message optional message
     */
    public ServerHealthEvent(
            @Nonnull ServerInstance instance,
            @Nonnull ServerStatus previousStatus,
            @Nonnull ServerStatus newStatus,
            String message) {
        this.instance = Objects.requireNonNull(instance, "instance");
        this.previousStatus = Objects.requireNonNull(previousStatus, "previousStatus");
        this.newStatus = Objects.requireNonNull(newStatus, "newStatus");
        this.message = message;
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
     * Get the previous status.
     *
     * @return previous status
     */
    @Nonnull
    public ServerStatus getPreviousStatus() {
        return previousStatus;
    }

    /**
     * Get the new status.
     *
     * @return new status
     */
    @Nonnull
    public ServerStatus getNewStatus() {
        return newStatus;
    }

    /**
     * Get the status change message.
     *
     * @return message, or null
     */
    public String getMessage() {
        return message;
    }

    /**
     * Check if the server became unhealthy.
     *
     * @return true if now unhealthy
     */
    public boolean becameUnhealthy() {
        return newStatus == ServerStatus.UNHEALTHY || newStatus == ServerStatus.FAILED;
    }

    /**
     * Check if the server recovered.
     *
     * @return true if recovered from unhealthy
     */
    public boolean recovered() {
        return (previousStatus == ServerStatus.UNHEALTHY || previousStatus == ServerStatus.FAILED)
                && newStatus == ServerStatus.RUNNING;
    }
}

package me.internalizable.numdrassl.servermanager.event;

import me.internalizable.numdrassl.servermanager.instance.ServerInstance;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Event fired when a server instance has been spawned and is running.
 *
 * <p>This event is fired after the server process has started and
 * is ready to accept connections.</p>
 */
public class ServerSpawnEvent {

    private final ServerInstance instance;

    /**
     * Create a server spawn event.
     *
     * @param instance the spawned server instance
     */
    public ServerSpawnEvent(@Nonnull ServerInstance instance) {
        this.instance = Objects.requireNonNull(instance, "instance");
    }

    /**
     * Get the spawned server instance.
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
     * Get the server port.
     *
     * @return port number
     */
    public int getPort() {
        return instance.getPort();
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
     * Get the template name (for dynamic servers).
     *
     * @return template name, or null for static servers
     */
    public String getTemplateName() {
        return instance.getTemplate() != null
                ? instance.getTemplate().getName()
                : null;
    }
}

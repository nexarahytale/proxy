package me.internalizable.numdrassl.servermanager.registry;

/**
 * Defines the type of a managed server.
 */
public enum ServerType {
    /**
     * Static servers are persistent and survive proxy restarts.
     * They are defined in configuration and always running.
     */
    STATIC,

    /**
     * Dynamic servers are ephemeral, spawned from templates on demand.
     * They are automatically cleaned up after shutdown and do not
     * persist across proxy restarts.
     */
    DYNAMIC
}

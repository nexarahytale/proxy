package me.internalizable.numdrassl.servermanager.instance;

/**
 * Represents the lifecycle status of a managed server instance.
 */
public enum ServerStatus {
    /**
     * Server instance has been created but not yet started.
     */
    CREATED,

    /**
     * Server process is starting up.
     */
    STARTING,

    /**
     * Server is running and accepting connections.
     * Bridge plugin has connected and authenticated.
     */
    RUNNING,

    /**
     * Server is in the process of graceful shutdown.
     * Players are being transferred to fallback servers.
     */
    STOPPING,

    /**
     * Server has stopped and process has terminated.
     */
    STOPPED,

    /**
     * Server failed to start or crashed unexpectedly.
     */
    FAILED,

    /**
     * Server is not responding to health checks.
     */
    UNHEALTHY;

    /**
     * Check if the server is in a terminal state.
     *
     * @return true if the server cannot transition to another state
     */
    public boolean isTerminal() {
        return this == STOPPED || this == FAILED;
    }

    /**
     * Check if the server is accepting players.
     *
     * @return true if players can connect to this server
     */
    public boolean isAcceptingPlayers() {
        return this == RUNNING;
    }

    /**
     * Check if the server process should be running.
     *
     * @return true if the process is expected to be alive
     */
    public boolean isProcessExpected() {
        return this == STARTING || this == RUNNING || this == STOPPING;
    }
}

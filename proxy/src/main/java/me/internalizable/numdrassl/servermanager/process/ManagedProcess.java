package me.internalizable.numdrassl.servermanager.process;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a managed server process.
 *
 * <p>Wraps a Java {@link Process} with additional metadata and
 * log buffering capabilities.</p>
 */
public class ManagedProcess {

    private static final int MAX_LOG_BUFFER = 1000;

    private final String serverId;
    private final Process process;
    private final Path workingDirectory;
    private final Path logFile;
    private final long startTime;
    private final LinkedList<String> logBuffer = new LinkedList<>();

    private volatile Integer exitCode = null;

    /**
     * Create a managed process.
     *
     * @param serverId server identifier
     * @param process the Java process
     * @param workingDirectory working directory
     * @param logFile path to log file
     */
    public ManagedProcess(
            @Nonnull String serverId,
            @Nonnull Process process,
            @Nonnull Path workingDirectory,
            @Nonnull Path logFile) {
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.process = Objects.requireNonNull(process, "process");
        this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory");
        this.logFile = Objects.requireNonNull(logFile, "logFile");
        this.startTime = System.currentTimeMillis();
    }

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
     * Get the underlying Java process.
     *
     * @return the process
     */
    @Nonnull
    public Process getProcess() {
        return process;
    }

    /**
     * Get the process ID.
     *
     * @return PID
     */
    public long getPid() {
        return process.pid();
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
     * Get the log file path.
     *
     * @return log file path
     */
    @Nonnull
    public Path getLogFile() {
        return logFile;
    }

    /**
     * Get the process start time.
     *
     * @return start time in milliseconds since epoch
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Get the process uptime.
     *
     * @return uptime in milliseconds
     */
    public long getUptime() {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Check if the process is alive.
     *
     * @return true if running
     */
    public boolean isAlive() {
        return process.isAlive();
    }

    /**
     * Get the exit code if the process has terminated.
     *
     * @return exit code, or null if still running
     */
    @Nullable
    public Integer getExitCode() {
        if (exitCode != null) {
            return exitCode;
        }
        if (!process.isAlive()) {
            exitCode = process.exitValue();
            return exitCode;
        }
        return null;
    }

    /**
     * Set the exit code (called by ProcessManager).
     *
     * @param exitCode the exit code
     */
    void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    /**
     * Add a log line to the buffer.
     *
     * @param line log line
     */
    synchronized void addLogLine(@Nonnull String line) {
        logBuffer.addLast(line);
        while (logBuffer.size() > MAX_LOG_BUFFER) {
            logBuffer.removeFirst();
        }
    }

    /**
     * Get recent log lines.
     *
     * @param count number of lines to retrieve
     * @return list of log lines (most recent last)
     */
    @Nonnull
    public synchronized List<String> getRecentLogs(int count) {
        if (count <= 0) {
            return Collections.emptyList();
        }

        int size = logBuffer.size();
        if (count >= size) {
            return new ArrayList<>(logBuffer);
        }

        List<String> result = new ArrayList<>(count);
        int skip = size - count;
        int i = 0;
        for (String line : logBuffer) {
            if (i >= skip) {
                result.add(line);
            }
            i++;
        }
        return result;
    }

    /**
     * Get all buffered log lines.
     *
     * @return list of all buffered log lines
     */
    @Nonnull
    public synchronized List<String> getAllLogs() {
        return new ArrayList<>(logBuffer);
    }

    /**
     * Clear the log buffer.
     */
    public synchronized void clearLogBuffer() {
        logBuffer.clear();
    }

    @Override
    public String toString() {
        return "ManagedProcess{" +
                "serverId='" + serverId + '\'' +
                ", pid=" + getPid() +
                ", alive=" + isAlive() +
                ", uptime=" + getUptime() + "ms" +
                '}';
    }
}

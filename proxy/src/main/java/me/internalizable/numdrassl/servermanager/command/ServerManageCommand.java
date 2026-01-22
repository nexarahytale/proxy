package me.internalizable.numdrassl.servermanager.command;

import me.internalizable.numdrassl.api.chat.ChatMessageBuilder;
import me.internalizable.numdrassl.api.command.Command;
import me.internalizable.numdrassl.api.command.CommandResult;
import me.internalizable.numdrassl.api.command.CommandSource;
import me.internalizable.numdrassl.servermanager.ServerManager;
import me.internalizable.numdrassl.servermanager.instance.InstanceManager;
import me.internalizable.numdrassl.servermanager.instance.ServerInstance;
import me.internalizable.numdrassl.servermanager.instance.ServerStatus;
import me.internalizable.numdrassl.servermanager.process.ManagedProcess;
import me.internalizable.numdrassl.servermanager.registry.ServerRegistry;
import me.internalizable.numdrassl.servermanager.registry.ServerType;
import me.internalizable.numdrassl.servermanager.template.ServerTemplate;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Command for managing servers (spawn, shutdown, list, info, etc.).
 *
 * <p>Usage:</p>
 * <ul>
 *   <li>{@code /srvmgr list [static|dynamic|all]} - List servers</li>
 *   <li>{@code /srvmgr info <server-id>} - Server details</li>
 *   <li>{@code /srvmgr spawn <template> [options]} - Spawn dynamic server</li>
 *   <li>{@code /srvmgr shutdown <server-id> [--force]} - Shutdown server</li>
 *   <li>{@code /srvmgr restart <server-id>} - Restart server</li>
 *   <li>{@code /srvmgr template list} - List templates</li>
 *   <li>{@code /srvmgr logs <server-id> [--tail=N]} - View logs</li>
 *   <li>{@code /srvmgr stats} - Show statistics</li>
 * </ul>
 */
public class ServerManageCommand implements Command {

    private final ServerManager serverManager;

    public ServerManageCommand(@Nonnull ServerManager serverManager) {
        this.serverManager = Objects.requireNonNull(serverManager, "serverManager");
    }

    @Override
    @Nonnull
    public String getName() {
        return "srvmgr";
    }

    @Override
    public String getDescription() {
        return "Manage dynamic and static servers";
    }

    @Override
    public String getUsage() {
        return "/srvmgr <list|info|spawn|shutdown|restart|template|logs|stats> [args]";
    }

    @Override
    public String getPermission() {
        return "numdrassl.admin.servermanager";
    }

    @Override
    @Nonnull
    public CommandResult execute(@Nonnull CommandSource source, @Nonnull String[] args) {
        if (!serverManager.isInitialized()) {
            source.sendMessage(ChatMessageBuilder.create()
                    .red("[X] ")
                    .gray("Server manager is not initialized."));
            return CommandResult.failure("Server manager not initialized");
        }

        if (args.length == 0) {
            return showHelp(source);
        }

        String subCommand = args[0].toLowerCase();
        String[] subArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];

        return switch (subCommand) {
            case "list", "ls" -> listServers(source, subArgs);
            case "info", "i" -> showInfo(source, subArgs);
            case "spawn", "start" -> spawnServer(source, subArgs);
            case "shutdown", "stop" -> shutdownServer(source, subArgs);
            case "restart" -> restartServer(source, subArgs);
            case "template", "templates", "t" -> templateCommand(source, subArgs);
            case "logs", "log" -> showLogs(source, subArgs);
            case "stats", "status" -> showStats(source);
            case "help", "?" -> showHelp(source);
            default -> {
                source.sendMessage(ChatMessageBuilder.create()
                        .red("[X] ")
                        .gray("Unknown subcommand: ")
                        .red(subCommand));
                yield showHelp(source);
            }
        };
    }

    private CommandResult showHelp(CommandSource source) {
        source.sendMessage(ChatMessageBuilder.create()
                .gold("------------------------------"));
        source.sendMessage(ChatMessageBuilder.create()
                .gold(">> ")
                .yellow("Server Manager Commands")
                .gold(" <<"));
        source.sendMessage(ChatMessageBuilder.create()
                .gold("------------------------------"));

        sendHelpLine(source, "list [static|dynamic]", "List servers");
        sendHelpLine(source, "info <server-id>", "Show server details");
        sendHelpLine(source, "spawn <template> [--max-players=N]", "Spawn dynamic server");
        sendHelpLine(source, "shutdown <server-id> [--force]", "Shutdown server");
        sendHelpLine(source, "restart <server-id>", "Restart server");
        sendHelpLine(source, "template list", "List templates");
        sendHelpLine(source, "logs <server-id> [--tail=N]", "View server logs");
        sendHelpLine(source, "stats", "Show statistics");

        source.sendMessage(ChatMessageBuilder.create()
                .gold("------------------------------"));

        return CommandResult.success();
    }

    private void sendHelpLine(CommandSource source, String usage, String description) {
        source.sendMessage(ChatMessageBuilder.create()
                .gray("  /srvmgr ")
                .yellow(usage)
                .darkGray(" - ")
                .gray(description));
    }

    // ==================== List ====================

    private CommandResult listServers(CommandSource source, String[] args) {
        ServerType filter = null;
        if (args.length > 0) {
            filter = switch (args[0].toLowerCase()) {
                case "static" -> ServerType.STATIC;
                case "dynamic" -> ServerType.DYNAMIC;
                case "all" -> null;
                default -> {
                    source.sendMessage(ChatMessageBuilder.create()
                            .red("[X] ")
                            .gray("Invalid filter. Use: static, dynamic, or all"));
                    yield null;
                }
            };
            if (args[0].equalsIgnoreCase("static") || args[0].equalsIgnoreCase("dynamic")) {
                // Valid filter, continue
            } else if (!args[0].equalsIgnoreCase("all")) {
                return CommandResult.failure("Invalid filter");
            }
        }

        List<ServerInstance> servers = serverManager.listServers(filter);

        source.sendMessage(ChatMessageBuilder.create()
                .gold("------------------------------"));
        source.sendMessage(ChatMessageBuilder.create()
                .gold(">> ")
                .yellow("Servers")
                .gray(filter != null ? " (" + filter.name().toLowerCase() + ")" : "")
                .gold(" <<"));
        source.sendMessage(ChatMessageBuilder.create()
                .gold("------------------------------"));

        if (servers.isEmpty()) {
            source.sendMessage(ChatMessageBuilder.create()
                    .gray("  No servers found."));
        } else {
            for (ServerInstance server : servers) {
                ChatMessageBuilder line = ChatMessageBuilder.create();

                // Status indicator
                switch (server.getStatus()) {
                    case RUNNING -> line.green("● ");
                    case STARTING -> line.yellow("◐ ");
                    case STOPPING -> line.yellow("◑ ");
                    case STOPPED -> line.gray("○ ");
                    case FAILED, UNHEALTHY -> line.red("✖ ");
                    default -> line.gray("? ");
                }

                // Server info
                line.white(server.getServerId())
                        .darkGray(" [")
                        .gray(server.getType().name().toLowerCase())
                        .darkGray("] ");

                // Port
                line.darkGray(":")
                        .aqua(String.valueOf(server.getPort()))
                        .darkGray(" ");

                // Players
                line.darkGray("(")
                        .yellow(String.valueOf(server.getPlayerCount()))
                        .darkGray("/")
                        .gray(String.valueOf(server.getMaxPlayers()))
                        .darkGray(")");

                source.sendMessage(line);
            }
        }

        source.sendMessage(ChatMessageBuilder.create()
                .gold("------------------------------"));
        source.sendMessage(ChatMessageBuilder.create()
                .gray("  Total: ")
                .white(String.valueOf(servers.size()))
                .gray(" server(s)"));

        return CommandResult.success();
    }

    // ==================== Info ====================

    private CommandResult showInfo(CommandSource source, String[] args) {
        if (args.length == 0) {
            source.sendMessage(ChatMessageBuilder.create()
                    .red("[X] ")
                    .gray("Usage: /srvmgr info <server-id>"));
            return CommandResult.failure("Missing server ID");
        }

        String serverId = args[0];
        ServerInstance server = serverManager.getServer(serverId);

        if (server == null) {
            source.sendMessage(ChatMessageBuilder.create()
                    .red("[X] ")
                    .gray("Server not found: ")
                    .red(serverId));
            return CommandResult.failure("Server not found");
        }

        source.sendMessage(ChatMessageBuilder.create()
                .gold("------------------------------"));
        source.sendMessage(ChatMessageBuilder.create()
                .gold(">> ")
                .yellow("Server: ")
                .white(serverId)
                .gold(" <<"));
        source.sendMessage(ChatMessageBuilder.create()
                .gold("------------------------------"));

        sendInfoLine(source, "Type", server.getType().name());
        sendInfoLine(source, "Status", server.getStatus().name());
        sendInfoLine(source, "Port", String.valueOf(server.getPort()));
        sendInfoLine(source, "Players", server.getPlayerCount() + "/" + server.getMaxPlayers());

        if (server.getTemplate() != null) {
            sendInfoLine(source, "Template", server.getTemplate().getName());
        }

        if (server.getStartedAt() != null) {
            Duration uptime = Duration.between(server.getStartedAt(), Instant.now());
            sendInfoLine(source, "Uptime", formatDuration(uptime));
        }

        ManagedProcess process = server.getProcess();
        if (process != null) {
            sendInfoLine(source, "PID", String.valueOf(process.getPid()));
            sendInfoLine(source, "Alive", process.isAlive() ? "Yes" : "No");
        }

        if (server.getLastHeartbeat() != null) {
            Duration since = Duration.between(server.getLastHeartbeat(), Instant.now());
            sendInfoLine(source, "Last Heartbeat", formatDuration(since) + " ago");
        }

        source.sendMessage(ChatMessageBuilder.create()
                .gold("------------------------------"));

        return CommandResult.success();
    }

    private void sendInfoLine(CommandSource source, String key, String value) {
        source.sendMessage(ChatMessageBuilder.create()
                .gray("  " + key + ": ")
                .white(value));
    }

    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        } else {
            return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        }
    }

    // ==================== Spawn ====================

    private CommandResult spawnServer(CommandSource source, String[] args) {
        if (args.length == 0) {
            source.sendMessage(ChatMessageBuilder.create()
                    .red("[X] ")
                    .gray("Usage: /srvmgr spawn <template> [--max-players=N] [--id=name]"));
            return CommandResult.failure("Missing template name");
        }

        String templateName = args[0];
        InstanceManager.SpawnOptions options = new InstanceManager.SpawnOptions();

        // Parse options
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--max-players=")) {
                try {
                    options.setMaxPlayers(Integer.parseInt(arg.substring(14)));
                } catch (NumberFormatException e) {
                    source.sendMessage(ChatMessageBuilder.create()
                            .red("[X] ")
                            .gray("Invalid max-players value"));
                    return CommandResult.failure("Invalid max-players");
                }
            } else if (arg.startsWith("--id=")) {
                options.setServerId(arg.substring(5));
            } else if (arg.startsWith("--memory=")) {
                options.setMemory(arg.substring(9));
            }
        }

        source.sendMessage(ChatMessageBuilder.create()
                .gold("[*] ")
                .gray("Spawning server from template: ")
                .yellow(templateName)
                .gray("..."));

        CompletableFuture<ServerInstance> future = serverManager.spawnDynamicServer(templateName, options);

        future.thenAccept(instance -> {
            source.sendMessage(ChatMessageBuilder.create()
                    .green("[✓] ")
                    .gray("Server spawned: ")
                    .green(instance.getServerId())
                    .gray(" on port ")
                    .aqua(String.valueOf(instance.getPort())));
        }).exceptionally(e -> {
            source.sendMessage(ChatMessageBuilder.create()
                    .red("[X] ")
                    .gray("Failed to spawn server: ")
                    .red(e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
            return null;
        });

        return CommandResult.success();
    }

    // ==================== Shutdown ====================

    private CommandResult shutdownServer(CommandSource source, String[] args) {
        if (args.length == 0) {
            source.sendMessage(ChatMessageBuilder.create()
                    .red("[X] ")
                    .gray("Usage: /srvmgr shutdown <server-id> [--force]"));
            return CommandResult.failure("Missing server ID");
        }

        String serverId = args[0];
        boolean force = args.length > 1 && args[1].equalsIgnoreCase("--force");

        ServerInstance server = serverManager.getServer(serverId);
        if (server == null) {
            source.sendMessage(ChatMessageBuilder.create()
                    .red("[X] ")
                    .gray("Server not found: ")
                    .red(serverId));
            return CommandResult.failure("Server not found");
        }

        source.sendMessage(ChatMessageBuilder.create()
                .gold("[*] ")
                .gray("Shutting down server: ")
                .yellow(serverId)
                .gray(force ? " (forced)" : "")
                .gray("..."));

        serverManager.shutdownServer(serverId, force)
                .thenRun(() -> {
                    source.sendMessage(ChatMessageBuilder.create()
                            .green("[✓] ")
                            .gray("Server ")
                            .green(serverId)
                            .gray(" shut down successfully."));
                })
                .exceptionally(e -> {
                    source.sendMessage(ChatMessageBuilder.create()
                            .red("[X] ")
                            .gray("Failed to shutdown: ")
                            .red(e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
                    return null;
                });

        return CommandResult.success();
    }

    // ==================== Restart ====================

    private CommandResult restartServer(CommandSource source, String[] args) {
        if (args.length == 0) {
            source.sendMessage(ChatMessageBuilder.create()
                    .red("[X] ")
                    .gray("Usage: /srvmgr restart <server-id>"));
            return CommandResult.failure("Missing server ID");
        }

        String serverId = args[0];

        source.sendMessage(ChatMessageBuilder.create()
                .gold("[*] ")
                .gray("Restarting server: ")
                .yellow(serverId)
                .gray("..."));

        serverManager.restartServer(serverId)
                .thenAccept(instance -> {
                    source.sendMessage(ChatMessageBuilder.create()
                            .green("[✓] ")
                            .gray("Server ")
                            .green(serverId)
                            .gray(" restarted on port ")
                            .aqua(String.valueOf(instance.getPort())));
                })
                .exceptionally(e -> {
                    source.sendMessage(ChatMessageBuilder.create()
                            .red("[X] ")
                            .gray("Failed to restart: ")
                            .red(e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
                    return null;
                });

        return CommandResult.success();
    }

    // ==================== Template ====================

    private CommandResult templateCommand(CommandSource source, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            return listTemplates(source);
        }

        source.sendMessage(ChatMessageBuilder.create()
                .red("[X] ")
                .gray("Usage: /srvmgr template list"));
        return CommandResult.failure("Invalid template subcommand");
    }

    private CommandResult listTemplates(CommandSource source) {
        Collection<ServerTemplate> templates = serverManager.getAllTemplates();

        source.sendMessage(ChatMessageBuilder.create()
                .gold("------------------------------"));
        source.sendMessage(ChatMessageBuilder.create()
                .gold(">> ")
                .yellow("Templates")
                .gold(" <<"));
        source.sendMessage(ChatMessageBuilder.create()
                .gold("------------------------------"));

        if (templates.isEmpty()) {
            source.sendMessage(ChatMessageBuilder.create()
                    .gray("  No templates found."));
        } else {
            for (ServerTemplate template : templates) {
                ChatMessageBuilder line = ChatMessageBuilder.create()
                        .gray("  - ")
                        .white(template.getName());

                if (template.getMetadata() != null) {
                    line.darkGray(" (")
                            .gray("max: ")
                            .yellow(String.valueOf(template.getMetadata().getMaxPlayers()))
                            .darkGray(", ")
                            .gray("mem: ")
                            .yellow(template.getMetadata().getMemoryAllocation())
                            .darkGray(")");
                }

                source.sendMessage(line);
            }
        }

        source.sendMessage(ChatMessageBuilder.create()
                .gold("------------------------------"));

        return CommandResult.success();
    }

    // ==================== Logs ====================

    private CommandResult showLogs(CommandSource source, String[] args) {
        if (args.length == 0) {
            source.sendMessage(ChatMessageBuilder.create()
                    .red("[X] ")
                    .gray("Usage: /srvmgr logs <server-id> [--tail=N]"));
            return CommandResult.failure("Missing server ID");
        }

        String serverId = args[0];
        int lines = 20;

        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--tail=")) {
                try {
                    lines = Integer.parseInt(args[i].substring(7));
                } catch (NumberFormatException e) {
                    // Use default
                }
            }
        }

        List<String> logs = serverManager.getProcessManager().getRecentLogs(serverId, lines);

        if (logs.isEmpty()) {
            source.sendMessage(ChatMessageBuilder.create()
                    .yellow("[!] ")
                    .gray("No logs available for: ")
                    .yellow(serverId));
            return CommandResult.success();
        }

        source.sendMessage(ChatMessageBuilder.create()
                .gold("------------------------------"));
        source.sendMessage(ChatMessageBuilder.create()
                .gold(">> ")
                .yellow("Logs: ")
                .white(serverId)
                .gray(" (last " + logs.size() + " lines)")
                .gold(" <<"));
        source.sendMessage(ChatMessageBuilder.create()
                .gold("------------------------------"));

        for (String line : logs) {
            source.sendMessage(ChatMessageBuilder.create()
                    .gray(line));
        }

        source.sendMessage(ChatMessageBuilder.create()
                .gold("------------------------------"));

        return CommandResult.success();
    }

    // ==================== Stats ====================

    private CommandResult showStats(CommandSource source) {
        ServerRegistry.RegistryStats stats = serverManager.getStats();

        source.sendMessage(ChatMessageBuilder.create()
                .gold("------------------------------"));
        source.sendMessage(ChatMessageBuilder.create()
                .gold(">> ")
                .yellow("Server Manager Stats")
                .gold(" <<"));
        source.sendMessage(ChatMessageBuilder.create()
                .gold("------------------------------"));

        sendInfoLine(source, "Total Servers", String.valueOf(stats.totalServers()));
        sendInfoLine(source, "Static Servers", String.valueOf(stats.staticServers()));
        sendInfoLine(source, "Dynamic Servers", String.valueOf(stats.dynamicServers()));
        sendInfoLine(source, "Running Servers", String.valueOf(stats.runningServers()));
        sendInfoLine(source, "Total Players", String.valueOf(stats.totalPlayers()));
        sendInfoLine(source, "Templates", String.valueOf(serverManager.getAllTemplates().size()));

        source.sendMessage(ChatMessageBuilder.create()
                .gold("------------------------------"));

        return CommandResult.success();
    }

    @Override
    @Nonnull
    public List<String> suggest(@Nonnull CommandSource source, @Nonnull String[] args) {
        if (args.length == 1) {
            return filterStartsWith(args[0],
                    "list", "info", "spawn", "shutdown", "restart", "template", "logs", "stats", "help");
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            return switch (subCommand) {
                case "list", "ls" -> filterStartsWith(args[1], "static", "dynamic", "all");
                case "info", "shutdown", "restart", "logs" -> filterStartsWith(args[1],
                        serverManager.getRegistry().getServerIds());
                case "spawn" -> filterStartsWith(args[1],
                        serverManager.getTemplateManager().getTemplateNames());
                case "template" -> filterStartsWith(args[1], "list");
                default -> Collections.emptyList();
            };
        }

        return Collections.emptyList();
    }

    private List<String> filterStartsWith(String prefix, String... options) {
        return filterStartsWith(prefix, Arrays.asList(options));
    }

    private List<String> filterStartsWith(String prefix, Collection<String> options) {
        String lowerPrefix = prefix.toLowerCase();
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(lowerPrefix))
                .toList();
    }
}

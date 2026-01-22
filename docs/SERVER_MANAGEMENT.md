# Numdrassl Server Management System

CloudNet-style server management for Hytale backend servers.

## Overview

The server management system provides dynamic server spawning, template-based deployment, and lifecycle management for Hytale backend servers. It supports both static (persistent) and dynamic (ephemeral) servers.

## Directory Structure

```
servers/
├── config.yml           # Server management configuration
├── templates/           # Template definitions for dynamic spawning
│   ├── bedwars/
│   │   ├── template.yml
│   │   ├── server.properties
│   │   ├── startup.sh
│   │   ├── plugins/
│   │   │   └── NumdrasslBridge.jar
│   │   └── world/
│   ├── skywars/
│   └── lobby/
├── static/              # Persistent servers (survive restarts)
│   └── lobby/
│       ├── server.properties
│       ├── startup.sh
│       ├── data.yml
│       └── plugins/
├── dynamic/             # Runtime-spawned servers (ephemeral)
│   ├── bedwars-1/
│   └── skywars-2/
└── logs/
    ├── static/
    └── dynamic/
```

## Server Types

### Static Servers
- Persistent servers defined in `config.yml`
- Survive proxy restarts
- Data stored in `servers/static/<name>/`
- Always-on option for automatic startup

### Dynamic Servers
- Spawned from templates on demand
- Automatically cleaned up after shutdown
- Do not persist across proxy restarts
- Ideal for minigames, arenas, etc.

## Configuration

### servers/config.yml

```yaml
# Java executable path
javaPath: java

# Default fallback server
defaultFallbackServer: lobby

# Health check interval
healthCheckIntervalSeconds: 30

# Dynamic spawning settings
dynamicSpawning:
  enabled: true
  autoCleanup: true
  maxConcurrent: 50

# Port allocation
portAllocation:
  staticRangeStart: 6000
  staticRangeEnd: 6050
  dynamicRangeStart: 6100
  dynamicRangeEnd: 6500

# Static servers
staticServers:
  lobby:
    port: 6000
    maxPlayers: 100
    alwaysOn: true
    memory: 2G

# Templates
templates:
  bedwars:
    displayName: Bedwars
    maxPlayers: 16
    portRangeStart: 6100
    portRangeEnd: 6200
    memory: 2G
    worldReset: true
    autoCleanupDelaySeconds: 300
```

### Template Metadata (template.yml)

```yaml
name: Bedwars
type: minigame
serverIdPrefix: bedwars
maxPlayers: 16
memoryAllocation: 2G
worldResetOnShutdown: true
pluginLoader: plugins/NumdrasslBridge.jar
startupCommand: ./startup.sh
gracefulShutdownTimeout: 30
respawnLocation:
  x: 0.0
  y: 100.0
  z: 0.0
```

## Commands

### /srvmgr (Server Manager)

| Command | Description |
|---------|-------------|
| `/srvmgr list [static\|dynamic]` | List all servers |
| `/srvmgr info <server-id>` | Show server details |
| `/srvmgr spawn <template> [options]` | Spawn dynamic server |
| `/srvmgr shutdown <server-id> [--force]` | Shutdown server |
| `/srvmgr restart <server-id>` | Restart server |
| `/srvmgr template list` | List available templates |
| `/srvmgr logs <server-id> [--tail=N]` | View server logs |
| `/srvmgr stats` | Show statistics |

### Spawn Options

```
/srvmgr spawn bedwars --max-players=8 --id=bedwars-ranked-1 --memory=4G
```

## Plugin API

### Spawning a Server

```java
ServerManagerAPI api = proxy.getServerManager().getAPI();

// Spawn with options
api.spawnServer("bedwars", SpawnOptions.builder()
    .maxPlayers(8)
    .metadata("gameMode", "ranked")
    .build())
    .thenAccept(info -> {
        System.out.println("Server spawned: " + info.getServerId());
        System.out.println("Port: " + info.getPort());
    });

// Spawn with defaults
api.spawnServer("bedwars")
    .thenAccept(info -> {
        // Server ready
    });
```

### Shutting Down a Server

```java
api.shutdownServer("bedwars-1")
    .thenRun(() -> {
        System.out.println("Server shut down");
    });

// Force shutdown
api.shutdownServer("bedwars-1", true);
```

### Querying Servers

```java
// Get specific server
ServerInfo info = api.getServer("bedwars-1");

// Get all servers
Collection<ServerInfo> all = api.getAllServers();

// Get by type
Collection<ServerInfo> dynamic = api.getServersByType("DYNAMIC");

// Get by template
Collection<ServerInfo> bedwars = api.getServersByTemplate("bedwars");

// Find available server
ServerInfo available = api.findAvailableServer("bedwars");
```

### Listening to Events

```java
@Subscribe
public void onServerSpawn(ServerSpawnEvent event) {
    System.out.println("Server spawned: " + event.getServerId());
    System.out.println("Template: " + event.getTemplateName());
}

@Subscribe
public void onServerShutdown(ServerShutdownEvent event) {
    System.out.println("Server shutting down: " + event.getServerId());
    System.out.println("Reason: " + event.getReason());
}
```

## Server Lifecycle

```
CREATED → STARTING → RUNNING → STOPPING → STOPPED
              ↓         ↓
           FAILED   UNHEALTHY
```

### States

| State | Description |
|-------|-------------|
| CREATED | Instance created, not started |
| STARTING | Process spawning |
| RUNNING | Ready for players |
| STOPPING | Graceful shutdown in progress |
| STOPPED | Process terminated |
| FAILED | Startup or runtime failure |
| UNHEALTHY | Health check failed |

## Bridge Integration

Dynamic servers require the NumdrasslBridge plugin for proxy communication:

1. Place `NumdrasslBridge.jar` in template's `plugins/` directory
2. Configure shared secret in bridge config
3. Bridge authenticates with proxy on startup
4. Players are routed through proxy to backend

## Health Checks

The system performs periodic health checks:

- Process alive check
- Heartbeat timeout detection
- Automatic cleanup of crashed servers
- Status events for monitoring

## Best Practices

1. **Templates**: Keep templates minimal, include only necessary files
2. **Memory**: Configure appropriate memory per template type
3. **Cleanup**: Enable auto-cleanup for dynamic servers
4. **Ports**: Ensure port ranges don't overlap
5. **Bridge**: Always include NumdrasslBridge in templates
6. **Logging**: Monitor logs for startup issues

## Troubleshooting

### Server Won't Start
- Check `servers/logs/` for error messages
- Verify server JAR exists in template
- Check port availability
- Verify Java path in config

### Players Can't Connect
- Ensure NumdrasslBridge is installed
- Check shared secret matches proxy
- Verify server is in RUNNING state
- Check proxy server registry

### High Memory Usage
- Reduce `maxConcurrent` in config
- Lower memory allocation per template
- Enable aggressive cleanup

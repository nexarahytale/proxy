#!/bin/bash
# Hytale Server Startup Script - Bedwars

MEMORY="${MEMORY:-2G}"

java -Xms${MEMORY} -Xmx${MEMORY} \
    -XX:+UseG1GC \
    -XX:+ParallelRefProcEnabled \
    -XX:MaxGCPauseMillis=200 \
    -jar HytaleServer.jar \
    --assets Assets.zip \
    --auth-mode insecure \
    --transport QUIC \
    "$@"

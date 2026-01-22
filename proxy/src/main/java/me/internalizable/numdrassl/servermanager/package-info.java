/**
 * CloudNet-style server management system for Numdrassl.
 *
 * <p>This package provides dynamic server spawning, template-based deployment,
 * and lifecycle management for Hytale backend servers.</p>
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.servermanager.ServerManager} - Main orchestrator</li>
 *   <li>{@link me.internalizable.numdrassl.servermanager.template.TemplateManager} - Template discovery and cloning</li>
 *   <li>{@link me.internalizable.numdrassl.servermanager.instance.InstanceManager} - Running server instances</li>
 *   <li>{@link me.internalizable.numdrassl.servermanager.process.ProcessManager} - JVM process spawning</li>
 *   <li>{@link me.internalizable.numdrassl.servermanager.registry.ServerRegistry} - Server tracking</li>
 * </ul>
 *
 * <h2>Server Types</h2>
 * <ul>
 *   <li><b>Static</b> - Persistent servers that survive proxy restarts</li>
 *   <li><b>Dynamic</b> - Ephemeral servers spawned from templates, auto-cleaned</li>
 * </ul>
 *
 * @see me.internalizable.numdrassl.servermanager.ServerManager
 */
package me.internalizable.numdrassl.servermanager;

# Unplugged AFK for NeoForge

The idea for this mod originated from [Unplugged-AFK by Sakura-Ryoko](https://github.com/sakura-ryoko/unplugged-afk). This project is an independent **NeoForge implementation** of that concept.

Unplugged AFK lets you leave a server without leaving your computer running beside an idle Minecraft window. Use `/unplug` and a server-side stand-in remains at your current location, allowing nearby farms and other player-dependent systems to continue working while your real client is disconnected.

## Features

- **Go unplugged:** Disconnect safely while your server-side stand-in stays at the farm.
- **No client mod required:** Install the mod on the NeoForge server; connecting players do not need it.
- **Flexible sessions:** Choose a duration and an optional reason, or use the server's configured default timeout.
- **Seamless return:** Reconnecting replaces the stand-in with the real player and restores normal play.
- **Restart recovery:** Active unplugged sessions are persisted and restored after a server restart.
- **Safety controls:** Server owners can configure damage handling, death behavior, visibility, status messages, and command permissions.
- **Admin tools:** Operators can inspect, spawn, remove, list, save, reload, and repair unplugged sessions.

## Player commands

- `/unplug [minutes] [reason]` — leave a stand-in behind and disconnect.
- `/afk [minutes] [reason]` — optional alias, disabled by default.

The owner of an integrated single-player server cannot use `/unplug`.

## Administration

The `/unplugged-admin` command provides session information and management tools, including `info`, `list`, `spawn`, `kick`, `purge`, `save`, `reload`, and configurable advanced options.

Configuration is generated at `config/unplugged_afk.json`. It includes session timeouts, permissions, message formatting, damage and death behavior, player visibility, and other server-side controls.

## Requirements

- NeoForge
- A dedicated or multiplayer server
- The Java version required by the selected Minecraft/NeoForge release

Use the file made for your exact Minecraft version. Builds for different Minecraft releases are not interchangeable.

## Attribution

This NeoForge implementation is based on the idea and behavior of Sakura-Ryoko's original Fabric mod. It is independently maintained and is not endorsed by Sakura-Ryoko or NeoForged.

Licensed under LGPL-3.0.

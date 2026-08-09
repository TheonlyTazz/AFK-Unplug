# Unplugged AFK for NeoForge

An LGPL-3.0 NeoForge port of
[Sakura-Ryoko's Unplugged-AFK](https://github.com/sakura-ryoko/unplugged-afk).
It lets a player disconnect while a server-side replacement remains loaded at
their farm. No client installation is required.

## Supported branches

| Branch | Minecraft | NeoForge | Java |
| --- | --- | --- | --- |
| `neoforge-1.21.1` | 1.21.1 | 21.1.244 or newer 21.1.x | 21 |
| `neoforge-26.1.2` | 26.1.2 | 26.1.2.94 or newer compatible build | 25 |

Use the JAR built from the branch matching the server. The two Minecraft
versions are not binary-compatible.

> **Java is version-specific:** launch Minecraft 1.21.1 with Java 21 and
> Minecraft 26.1.2 with Java 25. In particular, do not run the 1.21.1
> NeoForge/FML stack on Java 25; older loader/coremod code can fail during
> bootstrap with an `InaccessibleObjectException` mentioning
> `MethodHandles.Lookup.IMPL_LOOKUP` before this mod is initialized.

## Commands

- `/unplug [minutes] [reason]` leaves an offline replacement and disconnects
  the caller. The integrated-server owner cannot use it.
- `/afk` is an optional alias, disabled by default.
- `/unplugged-admin info [player]`
- `/unplugged-admin list`
- `/unplugged-admin save|reload|purge`
- `/unplugged-admin spawn <player> [minutes] [reason]`
- `/unplugged-admin kick <player>`
- `/unplugged-admin set enabled|disableDamage|defaultTimeout <value>` when
  `advancedAdminOptions` is enabled.

Configuration is generated at `config/unplugged_afk.json`. Active and ended
session state is stored in `<world>/unplugged_afk_sessions.json`; normal
Minecraft playerdata remains authoritative for inventory, effects, position,
dimension, game mode and other entity state.

## Build

Check out the desired branch and run:

```powershell
.\gradlew.bat clean build
```

The distributable JAR is written to `build/libs`. On Linux/macOS, use
`./gradlew clean build` instead. The first build downloads and prepares the
matching Minecraft and NeoForge development artifacts.

For a dedicated-server development launch:

```powershell
.\gradlew.bat runServer
```

Accept the EULA in the generated versioned run directory first (for example,
`run-1.21.1/eula.txt`). Dedicated-server testing
is strongly recommended because fake players exercise login, playerdata and
chunk-tracking code that a client-only launch does not cover.

## Implementation notes

- Replacements are real `ServerPlayer` subclasses with an inert embedded
  connection, so vanilla farms and chunk tracking see a player entity.
- The real player is saved before replacement. Vanilla playerdata is loaded
  for both newly created and restart-restored replacements.
- Timeout, death, administrator removal and real-player reconnection transition
  the persisted session state and notify registered API listeners.
- Visibility mode removes hidden replacements from both entity tracking and
  the player-info list, with a separate operator exception.


## License and attribution

This project and the upstream-derived work are licensed under LGPL-3.0. See
`LICENSE` and `NOTICE`. This port is independent and is not endorsed by the
original author or NeoForged.

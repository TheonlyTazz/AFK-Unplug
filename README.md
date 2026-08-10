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
- `/unplugged-admin set <section.option> <value>` can update any primitive
  configuration field when `main.advancedAdminOptions` is enabled. Command
  suggestions list the available dotted option names; use `&` for color codes.

Configuration is generated at `config/unplugged_afk.json`. Active and ended
session state is stored in `<world>/unplugged_afk_sessions.json`; normal
Minecraft playerdata remains authoritative for inventory, effects, position,
dimension, game mode and other entity state.

Access can be controlled with `access.mode` (`EVERYONE`, `ALLOWLIST`, or
`DENYLIST`) and UUIDs in `access.players`. Names are accepted for convenience,
but UUIDs are recommended. Operators bypass access and duration limits by
default. `unplugged.maximumUnpluggedTimeout` caps a normal player's requested
duration, and `unplugged.maximumSimultaneousPlayers` protects the server from
too many representatives.

When FTB Ranks is installed, the optional integration recognizes
`unplugged_afk.use`, `unplugged_afk.auto`, `unplugged_afk.bypass_limits`, and
the numeric `unplugged_afk.duration.max` node. Explicit FTB Ranks values take
priority over the access list; the mod has no required FTB dependency.

Offline replacements show `PlayerName [UNPLUGGED]` above their head and in the TAB
list by default. Set `unplugged.showAfkNameplate` or
`unplugged.showAfkInTabList` to `false` to disable either indicator, and change
the `label.unplugged_afk.afk` entry through a resource pack to customize the
label. This deliberately distinguishes a disconnected representative from an
ordinary, still-connected AFK player. The existing
`unplugged.unpluggedHidePlayer` option still takes
precedence and hides both.

All player-facing text uses translation keys from
`assets/unplugged_afk/lang/en_us.json`; the server config contains behavior
and formatting options only.

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

### IntelliJ client launch recovery

The generated development runs use different Java versions. Set IntelliJ's
Gradle JVM and the Minecraft run configuration JRE to Java 21 on
`neoforge-1.21.1`, or Java 25 on `neoforge-26.1.2`. After changing branches or
JDKs, reload the Gradle project and regenerate the run files with:

```powershell
.\gradlew.bat neoForgeIdeSync prepareClientRun
```

This recreates `build/moddev/clientRunVmArgs.txt` and
`clientRunProgramArgs.txt`. If IntelliJ still launches 1.21.1 with Java 25,
delete its stale Minecraft client run configuration, reload Gradle, and use
the newly generated configuration. An error mentioning
`MethodHandles.Lookup.IMPL_LOOKUP` is a reliable sign that 1.21.1 was started
on the wrong JDK.

## Implementation notes

- Replacements are real `ServerPlayer` subclasses with an inert embedded
  connection, so vanilla farms and chunk tracking see a player entity.
- The real player is saved before replacement. Vanilla playerdata is loaded
  for both newly created and restart-restored replacements.
- Timeout, death, administrator removal and real-player reconnection transition
  the persisted session state and notify registered API listeners.
- Visibility mode removes hidden replacements from both entity tracking and
  the player-info list, with a separate operator exception.

## CurseForge publishing

The ready-to-paste CurseForge project page copy is in
[`metadata/curseforge-description.md`](metadata/curseforge-description.md).

Create a CurseForge project, copy `.env.example` to `.env`, and fill in:

```dotenv
CURSEFORGE_TOKEN=your-upload-token
CURSEFORGE_PROJECT_ID=your-numeric-project-id
CURSEFORGE_PROJECT_SLUG=your-project-slug
```

The `.env` file is ignored by Git. Publish the checked-out version branch with:

```powershell
.\gradlew.bat publishCurseforge
```

Publishing uses the JAR from `build/libs`, marks the release as alpha, attaches
the matching Minecraft version and NeoForge loader metadata, and requires a
non-empty `metadata/changelogs/<mod_version>.md` file before upload.


## License and attribution

This project and the upstream-derived work are licensed under LGPL-3.0. See
`LICENSE` and `NOTICE`. This port is independent and is not endorsed by the
original author or NeoForged.

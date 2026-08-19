# Arashi

A client-side Fabric mod for Minecraft **26.1.2+** that scans loaded chunks for
blocks you choose and renders them x-ray style (through terrain), with a
built-in self-updater.

## Requirements

- Minecraft 26.1.2 or newer
- [Fabric Loader](https://fabricmc.net/use/) 0.19.3+
- [Fabric API](https://modrinth.com/mod/fabric-api) matching your Minecraft version
- Java 25+ (bundled with modern Minecraft launchers)

Arashi is **client-side only**. It does not need to be installed on the
server, and works on vanilla or modded servers alike (modded blocks only
show up if the server actually has that mod, since the client needs to
receive them).

## Install

1. Install Fabric Loader and Fabric API for 26.1.2.
2. Drop `arashi.jar` into your `mods` folder.
3. Launch the game.

## Usage

- Press **B** (default, rebindable in Options > Controls > Key Binds > Arashi)
  or run **`/arashi`** in chat to open the block scanner screen.
- Search for a block by name/id and click an entry to toggle tracking it.
  Selections are saved immediately.
- Once a block is tracked, it will be highlighted through terrain in any
  chunk that is loaded (or loads later) within your render distance.

## Config

Tracked block ids are stored as JSON at:

```
<instance>/config/arashi.json
```

It's a plain list of block ids (e.g. `"minecraft:diamond_ore"`) and can be
hand-edited while the game is closed.

## Auto-updater

On startup, Arashi asynchronously checks
`https://api.github.com/repos/duetigh/arashi/releases/latest` and compares
the release tag against the running mod version (numeric, segment-by-segment
comparison - works for both semver-style tags and Minecraft's `year.drop.patch`
scheme).

If a newer release is found:

1. A chat message announces the update.
2. The new `arashi.jar` is downloaded into `mods/.arashi-update/` - the live
   `mods/arashi.jar` is never touched while the game is running, since the
   JVM holds a lock on it.
3. A shutdown hook moves the staged jar into `mods/arashi.jar` when the game
   next closes normally. The update is never force-applied or auto-restarted;
   the chat message is your only notice, and the swap happens silently on next launch.

For this to work, releases in the [GitHub repo](https://github.com/duetigh/arashi)
must be tagged (e.g. `v0.2.0`) and have an `arashi.jar` asset attached - the
included `.github/workflows/release.yml` does this automatically for any
pushed `v*.*.*` tag.

## Building from source

```
./gradlew build
```

The output is always `build/libs/arashi.jar`, regardless of the version set
in `gradle.properties`.

## Releasing

Push a tag matching `v*.*.*` (for example, `v0.1.0`). CI builds the JAR and
publishes it to the repository's Releases page with automatically generated
release notes:

```sh
git tag v0.1.0
git push origin v0.1.0
```

# CraftCanvas Ludo

A complete example addon built with the public CraftCanvas API. Play solo against 1-3 bots or create a room for 2-4 human and bot players. The project demonstrates screen registration in Kotlin, typed callbacks, per-player state updates and finite shader animation.

## Requirements

- Java 21 or newer.
- Paper 1.21.11 or a compatible Folia server.
- CraftCanvas 1.0.0-SNAPSHOT with `CanvasService.rendererCapabilities`, built against the same API artifact as this addon.
- CraftCanvas handles resource-pack generation, delivery and optional Nexo integration. The addon has no direct Nexo dependency.

## Installation

1. Install a compatible CraftCanvas build and place `CraftCanvasLudo.jar` in `plugins/`.
2. For dice animation, set `animation-enabled: true` in `CraftCanvasLudo/config.yml` and `shaders.animations: true` in `CraftCanvas/config.yml`.
3. Restart the server and rebuild the resource pack. If Nexo sends the pack, wait for its combined pack to rebuild/upload and accept the updated pack in the client.
4. Run `/ludo`.

The die rolls from the edge of the board to its centre and settles on the committed result. The number stays hidden during the throw. Gameplay also works with a static die when animation is disabled. The addon registers its game screens and PNG resources through the public API. Screens inherit the shared CraftCanvas `frame` and reuse its console, A/B, Home, Exit and button graphics. Keep the core `screens/frame.yml` and its native asset definitions installed.

## Commands

| Command | Purpose |
|---|---|
| `/ludo` | Open the menu |
| `/ludo solo <1-3>` | Play against bots |
| `/ludo create` | Create a multiplayer room |
| `/ludo join <code>` | Join a room |
| `/ludo bot add` / `/ludo bot remove` | Manage bots as the host |
| `/ludo ready` | Toggle readiness |
| `/ludo colour` | Choose an available colour |
| `/ludo start` | Start a ready lobby as the host |
| `/ludo hide` / `/ludo resume` | Hide or reopen the game without leaving |
| `/ludo leave` | Leave the room |
| `/ludo debug` | Show view and animation diagnostics |

In solo setup, select `1 bot`, `2 bots` or `3 bots`, then press `Start game` or A. Yellow brackets mark the selected count. On the board, A and `Roll dice` roll during your turn. Select a highlighted pawn to move. B returns to the menu; Exit closes the view. Use Exit or `/ludo hide` for an explicit close because Escape is not reliably observable through the book protocol.

`ludo.play` defaults to everyone. `ludo.admin` defaults to operators and permits diagnostics.

## Rules

Each player has four pawns. A six brings a pawn out of the base and grants another roll. Friendly pawns can share squares; landing on opponents sends their pawns back to base. All four starting squares are safe for every colour: pawns sharing them cannot capture one another, including when leaving the base. Shared safe squares show each colour in its own quarter. Home lanes belong to their colour and require an exact roll to finish. The first player to finish all four pawns wins. There are no three-roll attempts, three-six penalties or bonus rolls for captures and finishes.

Bots follow the same rules. Disconnected players retain their seats for the configured grace period, then become bots. Rooms without humans are removed. Games are held in memory and end when the plugin stops.

## Configuration

| Setting | Default | Meaning |
|---|---|---|
| `animation-enabled` | `true` | Animate dice when CraftCanvas shaders are enabled |
| `bot-delay-ticks` | `14` | Bot thinking delay, clamped to 1-200 ticks |
| `disconnect-grace-seconds` | `90` | Seat reservation after disconnect, clamped to 1-3600 seconds |
| `max-rooms` | `100` | Room capacity, clamped to 1-10000 |

`messages.yml` contains English interface text. The file is created on first startup; existing files are read without rewriting them. Bundled PNGs are copied to `art/` only when missing, so local artwork changes are preserved. Restart after changing animation mode so screen registration matches the core settings.

## Build

```sh
./gradlew build
```

The addon resolves `zone.vao:craftcanvas-api:1.0.0-SNAPSHOT` from the configured Maven repository as a compile-only dependency. Use `-PcraftcanvasApiVersion=<version>` to select a matching published API version. The artifact must include `CanvasService.rendererCapabilities` and match the installed CraftCanvas core.

The JAR is written to `build/libs/CraftCanvasLudo-1.0.0.jar`. The server supplies CraftCanvas and Paper; the addon packages the Kotlin runtime. GitHub Actions uses the same build command. No tests or test dependencies are included in this example.

## How the integration works

Start with [LudoPlugin.kt](src/main/kotlin/zone/vao/craftcanvas/ludo/LudoPlugin.kt), then follow these files:

| File or package | Responsibility |
|---|---|
| `canvas/LudoActions.kt` | Registers a typed `op` parameter and routes validated button callbacks to the game |
| `ui/LudoScreens.kt` | Builds menus, board and six dice timelines with `CanvasScreenDefinition`, inheriting the shared core frame |
| `assets/LudoAssets.kt` | Installs game-specific PNGs and declares their IDs and dimensions; shared console artwork comes from core |
| `ui/LudoViews.kt` | Opens screens, binds player sessions and updates declared variables and component appearances |
| `canvas/LudoListener.kt` | Connects CraftCanvas open/close events and player reconnects to view state |
| `game/` | Owns rooms, rules, turns, bots and dice results without importing Bukkit or CraftCanvas |
| `scheduler/ServerClock.kt` | Schedules game delays through the Paper/Folia global scheduler |
| `config/` and `command/` | Load addon settings/messages and handle `/ludo` |

Startup waits for `CanvasApi.whenReady(owner)`. On the global/main thread, it registers the typed callback and code-defined screens, retains their `CanvasRegistration` handles, and calls `reload()` once. Completion means the definitions and pack were prepared; it does not mean every client has downloaded the new pack. `openDetailed()` reports queued openings, and `ScreenOpenEvent` completes the view binding when the client is ready.

Shader support is read from `rendererCapabilities("book-glyph")`. The addon selects finite autoplay when `autoplay` is available and a static die otherwise. Each animated result uses the same declared frame timeline as the server-side roll delay, while `LudoViews` keeps the result hidden until landing.

Buttons pass a declared `op` value to `ludo:action`. `CanvasActionSchema` rejects unknown operations. The callback checks permission and the current session; the game checks the room, turn and version before changing state. `LudoViews` uses `withPlayer()` and session IDs for player-specific updates, including on Folia.

The `game/` package contains the authoritative state. CraftCanvas session variables are a view of that state, not the game database. To adapt the example, define your screens in `LudoScreens`, expose allowed actions in `LudoActions`, and project the resulting game state in `LudoViews`. Shared geometry is kept in `BoardGeometry`.

On disable, the addon stops game jobs, releases its views and unregisters the retained handles in reverse order. It leaves resource-pack construction and Nexo events to CraftCanvas.

## License

[Non-Commercial Source Available License v1.0](LICENSE). Commercial use requires written permission from the copyright holder.

The Gradle wrapper and Kotlin runtime retain their Apache License 2.0 notices.

---
name: gametest-harness
description: Write, run and debug Fabric client game tests with net.ccbluex:gametest-harness on the shared game test server (Paper with an anticheat per bot). Use for ServerGameTest classes, anticheat verdicts, GAMETEST_* variables and CI setup.
---

# Game test harness

A `ServerGameTest` starts the real Minecraft client (Fabric client game test API), joins a Paper server under a
bot name of its own, gets fresh worlds there, runs the test body, and fails if the server objected to anything:
an anticheat flag or setback, a movement Paper corrected, a kick, a refused command, a server WARN or ERROR that
names the bot, or an ERROR the client logged. The server's state is the truth; the client only predicts.

## Setup

1. `fabricApi { configureTests { createSourceSet = true; modId = "<mod>-gametest"; enableGameTests = false } }`.
2. `"gametestImplementation"("net.ccbluex:gametest-harness:<version>+<mc>")` from `https://maven.ccbluex.net/releases`
   (snapshots: `/snapshots`, version ending `-SNAPSHOT`). Fabric API and Fabric Language Kotlin must be on the run.
3. In the gametest `fabric.mod.json`: every test class under `entrypoints."fabric-client-gametest"`, then
   `net.ccbluex.gametest.harness.ResultsGameTest` last, and `"gametest-harness": "*"` under `depends`.
4. Give `runClientGameTest` a Gradle `timeout`, and the CI job a `timeout-minutes`.
5. Headless: `SDL_VIDEO_FORCE_EGL=1 xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runClientGameTest`
   (Ubuntu needs `xvfb libegl1 libegl-mesa0`).

| Variable | Meaning |
| --- | --- |
| `GAMETEST_API_TOKEN` | LiquidBounce API token from https://liquidbounce.net/account. Needed for `test.ccbluex.net`. |
| `GAMETEST_SERVER_ANTICHEAT` | Values from docs/anticheats.md of CCBlueX/gametest, comma separated for several at once (`intave,mx`). CI runs one job per value. |
| `GAMETEST_SERVER_ADDRESS` | Own server, such as `localhost:25565` from `docker compose up` in CCBlueX/gametest. |

No token and no address: every `ServerGameTest` is skipped with one log line. `-Dgametest.only=A,B` runs the
classes `AGameTest` and `BGameTest` only. Bots of one token: at most 5 at a time.

## Anatomy

```kotlin
class LegitWalkGameTest : ServerGameTest({              // TestWorld.FLAT unless a world is passed first
    setBlock(origin.south(12), "minecraft:stone")         // fixtures are built on the server
    input.holdKey { it.keyUp }
    ticks(40)
    input.releaseKey { it.keyUp }
    // The server, not the client, says where the bot is
    val z = query("data get entity $playerName Pos[2]", Regex("""(-?[\d.]+)d$""")).groupValues[1].toDouble()
    check(z > origin.z + 7) { "walking got the bot only to z $z" }
})
```

A mod with state of its own subclasses `ServerGameTest` once: `awaitClient(context)` waits until the client may
join (default: title screen), `beforeJoin(context)` resets the mod (settings back to defaults, caches cleared),
`afterTest(context)` runs after every test. All three run on the test thread; reach the client with
`context.runOnClient { }`.

## API (package `net.ccbluex.gametest.harness`)

Scope (`GameTestScope`, the receiver of the test body):

- `origin`: where the bot starts, looking south. FLAT: grass at y -61, origin (0, -60, 0). `TestWorld.VOID`: stone
  platform, origin (0, 64, 0). `TestWorld.generated(seed, structures)`: real terrain, sealed glass room at (0, -40, 0).
  `TestWorld.generated(seed, origin) { prepare }`: real terrain with a start the test builds itself.
- `playerName`, `anticheats` (`Set<Anticheat>`): branch on it when only some anticheats catch something.
- `client { minecraft -> }`: run on the client thread and return a value. Never block inside it.
- `ticks(n)`, `awaitClient(what, timeout) { minecraft -> condition }`.
- `input`: Fabric `TestInput` (`holdKey { it.keyUp }`, `releaseKey`, `pressKey`, `lookAt`, `typeChars`, mouse).
- `run(command): Int`: a command as the session's console, returns its id; `server.result(id)` after `done`.
- `server.console`: everything the server said, in order: `ConsoleLine(level, message, report, command)`.
- `awaitConsole(what, since) { line -> }`: tick until a line from index `since` on matches.
- `unique(name)`: a server-wide unique name for scoreboard objectives, teams, bossbars, storage.
- `travel(pos, yaw, pitch, dimension)`: teleport and wait until the chunk is there, rendered and the anticheat lets go;
  setbacks on the way are excused. Use it for every move the test does not perform with input.
- `expectViolation(what) { }`: the block must make the server object; those objections are excused. Only for
  negative controls.
- `reconnect()`, `leave()` then `join()`: leave and come back into the same session (worlds survive, 60 s).
  `drop()`: the server disconnects the bot like a stopping server. Some anticheats check nothing for the first
  seconds after a join; the harness waits that long after every join before the test goes on.

Actions (`net.ccbluex.gametest.harness.action`): `setBlock`, `fill`, `summon(entity, pos, nbt)`, `give`,
`teleport`, `forceLoad`, `hangItemFrame`, `query(command, Regex)`, `awaitServer("<execute if condition>")`,
`awaitAnswer(command, expected)`, `aim(pos)`, `chat()`, `Minecraft.chat`, `chatContains`, `screenshot(name)`.

Verdict (`net.ccbluex.gametest.harness.verdict`): `ClientErrors.ignore("fragment")` for log noise of your mod that
is not a bug (network services unreachable on CI and the like). Never to hide a real error.

## Rules

- Real input over calling the game directly. Never `Thread.sleep`: between ticks the client is frozen and the
  server sees a lagging player. The scope's await helpers tick while they wait.
- Prove effects on the server (`awaitServer`, `query`), not only on the client.
- Wait until the client sees a fixture before acting on it; a click in the same tick hits air.
- Turn before you act (`aim`): a rotation and a click in one tick is what anticheats look for.
- Commands reach only the bot's own worlds and the bot. Name players by `playerName` or `@s`/`@a`/`@e` selectors;
  dimensions as `minecraft:overworld`, `minecraft:the_nether`, `minecraft:the_end` (created on first use).
  Refused: `stop`, `say`, `me`, `reload`, `function`, `schedule`, `op`, `kick`, `tick`, other plugins' commands.
  `difficulty`, `gamerule`, `time`, `weather` apply to the session's world only.
- Scoreboard objectives, teams, bossbars and command storage are shared by every test on the server:
  always `unique(name)`, and match on the returned name in selectors (`scores={${obj}=1..}`).
- Never weaken the verdict to get a pass. A flag is a finding: fix the client until the server is quiet.
- Command syntax is 26.x: loot conditions dispatch on `type`, item components in brackets
  (`minecraft:shears[damage=1]`), `item replace entity <name> hotbar.0 with ...`.

## Reading a failure

```text
(GameTest) AutoJump failed
java.lang.IllegalStateException: the server objected 2 time(s), see gametest/AutoJump.log
  FLAG GrimAC Simulation 1.0 5.000000
  SETBACK GrimAC 0.5 -60.0 3.21
```

`build/run/clientGameTest/gametest/<Test>.log` has every message the test sent (`>`) and got (`<`) with
timestamps. FLAG is `<anticheat> <check> <violations> <details>`; the check name says what the anticheat thinks
happened: movement physics, interaction geometry (reach, hitboxes), packet content and order, inventory clicks. NOTE
is a flag of a check the anticheat calls experimental: logged as a warning, never a failure.
`build/run/clientGameTest/logs/latest.log` is the client.

Common causes: a command sent right before the test ended (the harness waits for answers, but not for effects),
a fixture not yet loaded on the client, a module acting in the same tick it turned, two tests using the same
objective name, a test that expects a flag only one anticheat raises (branch on `anticheats`).

## Running the server yourself

`docker compose up --build` in [CCBlueX/gametest](https://github.com/CCBlueX/gametest) runs Paper with Grim and Intave
on `localhost:25565`; point `GAMETEST_SERVER_ADDRESS` at it.

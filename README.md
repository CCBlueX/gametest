# gametest

Client game tests for Fabric mods against a real Paper server, with an anticheat of your choice watching. Every
test joins under a bot name of its own, gets worlds of its own, drives them with commands, and fails when the
server or its anticheat objects to what the client did. Tests of many CI runs share one server without meeting.

```mermaid
flowchart LR
    test["your mod's game test<br>(harness)"] -- "bot, ticket" --> proxy["test.ccbluex.net"]
    proxy --> paper["Paper<br>GameTest plugin"]
    paper --- anticheats["anticheats,<br>one per bot"]
    test -. "commands, reports<br>gametest:control" .- paper
```

| Directory | What |
| --- | --- |
| `harness/` | The Fabric library tests are written with, published as `net.ccbluex:gametest-harness` |
| `plugin/` | The Paper plugin: sessions and their worlds, isolation, the control channel, anticheat reports |
| `server/` | The server image with its anticheats and configuration |
| `docs/` | [The anticheats](docs/anticheats.md) and [the protocol](docs/protocol.md) |
| `skills/` | [An agent skill](skills/gametest-harness/SKILL.md) with the harness API and the rules a test follows |

Minecraft 26.3 on Java 25.

## Writing tests

```kotlin
repositories {
    maven("https://maven.ccbluex.net/releases")
}

dependencies {
    // In the client game test source set that fabricApi.configureTests creates
    "gametestImplementation"("net.ccbluex:gametest-harness:1.0.3+26.3")
}
```

The harness needs Fabric API and Fabric Language Kotlin at runtime.

```kotlin
class AutoShearerGameTest : ServerGameTest({
    give("minecraft:shears")
    summon("minecraft:sheep", origin.south(2), "{NoAI:1b}")
    // ... switch on what is under test
    awaitServer("entity @e[type=minecraft:sheep,nbt={Sheared:1b}]")
})
```

Register each test as a `fabric-client-gametest` entrypoint, and `net.ccbluex.gametest.harness.ResultsGameTest`
last. Override `awaitClient`, `beforeJoin` and `afterTest` of `ServerGameTest` to put your mod's state in order.
A test passes with up to `violationLevel` (3) flags, corrections and errors, and a failed one runs again up to
`retries` (2) times; both can be overridden per test.

| Variable | Meaning |
| --- | --- |
| `GAMETEST_API_TOKEN` | The API token from your [LiquidBounce account](https://liquidbounce.net/account). Needed for the public server. |
| `GAMETEST_SERVER_ANTICHEAT` | Which [anticheats](docs/anticheats.md) watch the bots, comma separated: `grim` or `intave,mx`. |
| `GAMETEST_SERVER_ADDRESS` | Another server, such as `localhost:25565`; then no token is needed. |

Without a token or an address the tests are skipped. `-Dgametest.only=AutoShearer,Harness` runs only those.

## The public server

`test.ccbluex.net` lets bots in that show a ticket from the LiquidBounce API, which the harness gets with its
token while it logs in. Every bot is logged with the account behind its token, an account that misuses the server
is banned, and one account runs at most 5 bots at a time.

In GitHub Actions, give the job the token as a secret and run the suite once per anticheat:

```yaml
strategy:
  matrix:
    anticheat: [...]  # values from docs/anticheats.md
env:
  GAMETEST_API_TOKEN: ${{ secrets.LIQUIDBOUNCE_API_TOKEN }}
  GAMETEST_SERVER_ANTICHEAT: ${{ matrix.anticheat }}
```

## Running the server

```sh
./gradlew :plugin:build
docker compose up --build
GAMETEST_SERVER_ADDRESS=localhost:25565 ./gradlew runClientGameTest   # in the mod
```

Starting it means agreeing to the [Minecraft EULA](https://aka.ms/MinecraftEULA). It downloads Grim and Intave; the paid
anticheats are only on the public server.

## License

[GPL-3.0](LICENSE)

# Protocol

What the harness and the plugin say to each other. A client in another language that follows it can use the
server too.

## Names

A bot is named `GT` + its [anticheat's letter](anticheats.md) + `_` + up to 12 letters and digits.

## Login

Behind the public server's proxy, the bot answers a login plugin query on `gametest:login`, whose payload is a
32 byte nonce, with a ticket from `POST https://api.liquidbounce.net/api/v3/gametest/ticket`
(`{"name": ..., "nonce": <base64>}`, the API token as bearer). A ticket is valid for one name and one login.

## Control channel

A play-phase plugin channel `gametest:control`, both directions. A payload is one UTF-8 JSON object. Only test
players are heard.

Client to server:

| type | fields | meaning |
| --- | --- | --- |
| `open` | `world {type: "flat"\|"normal"\|"void", seed?: string, generatorSettings?: string, structures?: bool}`, `info {repository?, run?, test?}` | Create this player's session, or resume it after a reconnect (then `world` is ignored). |
| `command` | `id: int`, `line: string` | Run one command as the session's console. |
| `close` | | The test is over: the session ends as soon as this player quits (kick after 10 s if it does not). |
| `drop` | | Disconnect the player the way a stopping server does. The session survives for a reconnect. |

Server to client:

| type | fields | meaning |
| --- | --- | --- |
| `ready` | `session: string`, `resumed: bool`, `server: string`, `anticheat: string` | Worlds exist; commands are accepted. `anticheat` is `Vanilla`, or the anticheat plugin's name and version. |
| `busy` | `retryAfter: int` (seconds) | Session limit reached; ask again. |
| `closed` | | The answer to `close`. A client quits only after it, or the server may never read the `close`. |
| `output` | `id: int`, `message: string` | One feedback line of command `id`, as a console shows it. |
| `done` | `id: int`, `result: int` | Command `id` finished; `result` is what it returned, 0 when it failed. Sent after its output. |
| `report` | `report: "FLAG"\|"NOTE"\|"SETBACK"\|"FAILMOVE"\|"KICK"`, `message: string` | The server objected: FLAG `<check> <violations> <details>` and SETBACK `<x> <y> <z>` from the player's anticheat, FAILMOVE `<reason>` and KICK `<cause> <reason>` from Paper. NOTE is a FLAG of a check the anticheat calls experimental; it does not fail a test. |
| `log` | `level: "INFO"\|"WARN"\|"ERROR"`, `message: string` | A line of the server log that contains the player's name. |
| `error` | `message: string`, `id?: int` | Protocol misuse, or a refused command, which is followed by `done` with result 0. |

## Sessions

A session has an overworld, and a Nether and an End from the first command that names them; the client and the
commands see them as `minecraft:overworld`, `minecraft:the_nether` and `minecraft:the_end`, and the lobby as
`gametest:lobby`. `open` moves the player out of the lobby, and it stays out until the session ends; `ready` comes
once the player is in the session's overworld. A session lasts 30 minutes at most and waits 60 s for a player who
left without `close`.

`forceload add` and a teleport into chunks that are not loaded answer at once and load the chunks in the background,
not on the server thread, where generating them would stall every other session. The session's next commands run
once the chunks are there, in the order they came.

Commands run at permission level 2 and reach only the session's worlds and its own player:

- Only the vanilla commands a test needs are there; `stop`, `reload`, `datapack`, `function`, `say`, `locate`,
  `random`, other plugins' commands and anything a new Minecraft version adds are refused. So are `execute on`,
  `execute if function`, `scoreboard objectives setdisplay`, `place jigsaw`, interpreted NBT text and
  `allow_op_features`.
- Selectors find the session's own player and entities in its worlds, never other players. Score holders `*`, UUIDs
  and other players' names are refused.
- `max_block_modifications` stays at most 32768, `max_command_forks` 1024 (the session's default),
  `max_command_sequence_length` 65536 and `random_tick_speed` 100; `global_sound_events` stays off.
- At most 100 commands a second after a burst of 400, 16384 characters a line, 1024 entities across the session's
  worlds and 256 force-loaded chunks a world.

Scoreboard objectives, teams, boss bars and command storage are server-wide: name them uniquely. They are cleared
whenever no session is open.

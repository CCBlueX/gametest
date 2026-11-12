# Anticheats

Each bot picks one or more with `GAMETEST_SERVER_ANTICHEAT`, comma separated (`intave,mx`); its name carries the
choice as the letters between `GT` and `_` (`GTIX_...`).

| Anticheat | `GAMETEST_SERVER_ANTICHEAT` | Letter | `test.ccbluex.net` | `docker compose` |
| --- | --- | --- | --- | --- |
| None, only Paper's own movement checks | `vanilla` | `V` | yes | yes |
| [Grim](https://modrinth.com/plugin/grimac) (default) | `grim` | `G` | yes | yes |
| [Intave](https://modrinth.com/plugin/intave) | `intave` | `I` | yes | yes |
| [Vulcan](https://www.spigotmc.org/resources/vulcan-anti-cheat.83626/) | `vulcan` | `U` | yes | no |
| [Matrix](https://matrix.rip) | `matrix` | `M` | yes | no |
| [Spartan](https://spartan.top) | `spartan` | `S` | yes | no |
| [MX](https://github.com/kireikosasha/MX-Project), only aim in fights | `mx` | `X` | yes | no |
| [AntiCheatAddition](https://www.spigotmc.org/resources/anticheataddition.33590/), no movement | `anticheataddition` | `A` | yes | no |
| [FairPlay](https://builtbybit.com/resources/fairplay-combat-anticheat-local-ai.111265/), no movement | `fairplay` | `F` | yes | no |

Whatever any of the bot's anticheats flags counts against the test's violation level; the others leave
the bot alone. Some check nothing for a moment after a join or a teleport, which the harness waits out, as long as the
slowest of them.
Several anticheats see the same packets, but the first one to set a move back takes it from the others: a bad move
watched by Grim and Intave is flagged by Grim alone. Pair anticheats that check different things, such as `intave,mx`.

A server without one of the chosen anticheats refuses the session, and the test fails.

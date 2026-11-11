# Anticheats

Each bot picks one with `GAMETEST_SERVER_ANTICHEAT`; its name carries the choice as the letter after `GT`.

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

A test fails on whatever the bot's anticheat flags or sets back; the other anticheats leave the bot alone. Some
check nothing for a moment after a join or a teleport, which the harness waits out.

A server without the chosen anticheat refuses the session, and the test fails.

package net.ccbluex.gametest.harness

import net.ccbluex.gametest.harness.connection.GameTestEnvironment

/**
 * A game test in a singleplayer world of the client's integrated server, for what the client finds, shows or keeps
 * that no anticheat has to judge. The world is driven like a session, with the same commands, but nothing watches
 * the player, Paper's movement checks included. It runs only while `GAMETEST_SERVER_ANTICHEAT` is unset, and needs
 * neither token nor address.
 */
abstract class SingleplayerGameTest(world: TestWorld, body: GameTestScope.() -> Unit) : WorldGameTest(world, body) {

    constructor(body: GameTestScope.() -> Unit) : this(TestWorld.FLAT, body)

    override val singleplayer = true

    override fun isEnabled() = GameTestEnvironment.local

}

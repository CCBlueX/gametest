package net.ccbluex.gametest.harness

import net.ccbluex.gametest.harness.connection.GameTestEnvironment

/**
 * A game test on the game test server: the client joins under a name of its own, gets a session with fresh worlds
 * and the run's anticheats watching, and runs [body]. It runs while `GAMETEST_SERVER_ANTICHEAT` names anticheats and
 * `GAMETEST_API_TOKEN` or `GAMETEST_SERVER_ADDRESS` is set; with anticheats but neither of those it is skipped.
 */
abstract class ServerGameTest(world: TestWorld, body: GameTestScope.() -> Unit) : WorldGameTest(world, body) {

    constructor(body: GameTestScope.() -> Unit) : this(TestWorld.FLAT, body)

    override val singleplayer = false

    override fun isEnabled(): Boolean {
        if (GameTestEnvironment.local) {
            return false
        }
        if (!GameTestEnvironment.enabled) {
            GameTestResults.skipped()
        }
        return GameTestEnvironment.enabled
    }

}

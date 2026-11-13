package net.ccbluex.gametest.harness

import net.ccbluex.gametest.harness.connection.GameTestEnvironment
import net.ccbluex.gametest.harness.connection.returnToTitle
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

/**
 * A game test of the client alone on the title screen, with no world: commands, configs, registries, screens. It
 * runs [body] once, without retries, only while `GAMETEST_SERVER_ANTICHEAT` is unset. A failure is recorded and
 * fails the run at [ResultsGameTest]. The class name without `GameTest` is what `-Dgametest.only` selects.
 */
abstract class ClientGameTest(private val body: (ClientGameTestContext) -> Unit) : FabricClientGameTest {

    private val name = javaClass.simpleName.removeSuffix("GameTest")

    final override fun runTest(context: ClientGameTestContext) {
        Setups.awaitClient(context)
        if (!GameTestEnvironment.local || !GameTestEnvironment.isSelected(name)) {
            return
        }

        val started = System.nanoTime()
        val failure = runCatching {
            try {
                Setups.beforeTest(context)
                body(context)
            } finally {
                context.runOnClient<RuntimeException> { it.returnToTitle() }
                Setups.afterTest(context)
            }
        }.exceptionOrNull()
        GameTestResults.ran(name, started, failure)
    }

}

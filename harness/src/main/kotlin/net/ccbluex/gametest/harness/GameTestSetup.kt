package net.ccbluex.gametest.harness

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screens.TitleScreen

/**
 * How a mod puts its own state in order around every game test, whatever its kind. Register an implementation as the
 * Fabric entrypoint `gametest-setup`, a Kotlin `object` through the `kotlin` adapter; the harness runs every registered
 * one in entrypoint order, and [afterTest] in reverse. All methods run on the test thread.
 */
interface GameTestSetup {

    /** Waits until the client may play, once the title screen is up. Runs before every test, skipped or not. */
    fun awaitClient(context: ClientGameTestContext) = Unit

    /**
     * Runs before the test: after the account switch and before the join, or before a [ClientGameTest]'s body. Put
     * what the mod keeps between tests back to its defaults here.
     */
    fun beforeTest(context: ClientGameTestContext) = Unit

    /** Runs after every test that ran, once the client is back on the title screen, whether it passed or not. */
    fun afterTest(context: ClientGameTestContext) = Unit

}

/** Every mod's [GameTestSetup], run as one. */
internal object Setups {

    private const val ENTRYPOINT = "gametest-setup"
    private const val STARTUP_TIMEOUT = 20 * 60 * 5

    private val all: List<GameTestSetup> by lazy {
        FabricLoader.getInstance().getEntrypoints(ENTRYPOINT, GameTestSetup::class.java)
    }

    // The runner fails a test that ends off the title screen, a mod's startup screen included, skipped or not
    fun awaitClient(context: ClientGameTestContext) {
        context.waitFor({ it.gui.screen() is TitleScreen }, STARTUP_TIMEOUT)
        all.forEach { it.awaitClient(context) }
    }

    fun beforeTest(context: ClientGameTestContext) = all.forEach { it.beforeTest(context) }

    /** Runs every setup's [GameTestSetup.afterTest] even if one throws, then throws the first failure. */
    fun afterTest(context: ClientGameTestContext) {
        var failure: Throwable? = null
        all.asReversed().forEach {
            try {
                it.afterTest(context)
            } catch (thrown: Throwable) {
                failure?.addSuppressed(thrown) ?: run { failure = thrown }
            }
        }
        failure?.let { throw it }
    }

}

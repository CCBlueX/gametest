package net.ccbluex.gametest.harness

import net.ccbluex.gametest.harness.connection.ControlChannel
import net.ccbluex.gametest.harness.connection.GameTestEnvironment
import net.ccbluex.gametest.harness.connection.OfflineAccount
import net.ccbluex.gametest.harness.connection.ServerSession
import net.ccbluex.gametest.harness.connection.returnToTitle
import net.ccbluex.gametest.harness.verdict.ClientErrors
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.TitleScreen
import org.apache.logging.log4j.LogManager
import java.util.concurrent.ThreadLocalRandom
import kotlin.io.path.Path

/**
 * One game test on the game test server: the client joins under a name of its own, gets a session with fresh
 * worlds and the run's anticheat watching, runs [body], and the test passes if the server objected to nothing
 * and the client logged no error. The class name without `GameTest` is what `-Dgametest.only` selects.
 *
 * Without `GAMETEST_API_TOKEN` or `GAMETEST_SERVER_ADDRESS` the test is skipped. Outcomes are collected, so
 * [ResultsGameTest] has to be the last game test entrypoint of the mod. A mod puts its own state in order in
 * [awaitClient], [beforeJoin] and [afterTest].
 */
abstract class ServerGameTest(
    private val world: TestWorld,
    private val body: GameTestScope.() -> Unit,
) : FabricClientGameTest {

    constructor(body: GameTestScope.() -> Unit) : this(TestWorld.FLAT, body)

    private val name = javaClass.simpleName.removeSuffix("GameTest")

    final override fun runTest(context: ClientGameTestContext) {
        // The runner fails a test that ends off the title screen, a mod's startup screen included, skipped or not
        awaitClient(context)
        if (!GameTestEnvironment.enabled) {
            GameTestResults.skipped()
            return
        }
        if (!GameTestEnvironment.isSelected(name)) {
            return
        }

        val anticheat = GameTestEnvironment.anticheat
        val playerName = "GT${anticheat.letter}_${randomName()}"
        val account = context.computeOnClient<OfflineAccount, RuntimeException> {
            OfflineAccount.switch(it, playerName)
        }
        val server = ServerSession(context, Path("gametest", "$name.log"))
        context.runOnClient<RuntimeException> { ControlChannel.session = server }
        ClientErrors.reset()
        try {
            beforeJoin(context)
            val scope = GameTestScope(context, server, world, playerName, anticheat, name)
            scope.use {
                it.join()
                it.body()
            }
            val violations = scope.verdict.violations()
            check(violations.isEmpty()) {
                "the server objected ${violations.size} time(s), see ${server.log}\n" +
                    violations.joinToString("\n") { "  $it" }
            }
            ClientErrors.assertNone()
            GameTestResults.passed(name)
        } catch (failure: Throwable) {
            GameTestResults.failed(name, failure)
        } finally {
            // The runner ends the whole run if a test does not end on the title screen
            context.runOnClient<RuntimeException> {
                it.returnToTitle()
                ControlChannel.session = null
                account.restore(it)
            }
            afterTest(context)
        }
    }

    /** Waits until the client may join a server; the title screen unless the mod has more to wait for. */
    protected open fun awaitClient(context: ClientGameTestContext) {
        context.waitFor({ it.gui.screen() is TitleScreen }, STARTUP_TIMEOUT)
    }

    /**
     * Runs on the test thread after the account switch, before the client joins. Mods put what they keep between
     * tests back to its defaults here.
     */
    protected open fun beforeJoin(context: ClientGameTestContext) = Unit

    /** Runs on the test thread once the client is back on the title screen, whether the test passed or not. */
    protected open fun afterTest(context: ClientGameTestContext) = Unit

    private fun randomName(): String {
        val random = ThreadLocalRandom.current()
        return String(CharArray(NAME_LENGTH) { NAME_CHARACTERS[random.nextInt(NAME_CHARACTERS.length)] })
    }

    private companion object {
        const val STARTUP_TIMEOUT = 20 * 60 * 5
        const val NAME_LENGTH = 12
        const val NAME_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    }

}

/** Collects every test's outcome so one failure does not hide the others; [ResultsGameTest] reports them. */
object GameTestResults {

    private val logger = LogManager.getLogger("GameTest")
    private val failures = linkedMapOf<String, Throwable>()
    private var passed = 0
    private var skipped = false

    internal fun passed(test: String) {
        passed++
        logger.info("{} passed", test)
    }

    internal fun failed(test: String, failure: Throwable) {
        failures[test] = failure
        logger.error("{} failed", test, failure)
    }

    internal fun skipped() {
        if (!skipped) {
            skipped = true
            logger.info("Skipping the server game tests: neither GAMETEST_API_TOKEN nor GAMETEST_SERVER_ADDRESS is set")
        }
    }

    fun assertAllPassed() {
        if (passed == 0 && failures.isEmpty()) {
            return
        }
        logger.info("{} server game test(s) passed, {} failed", passed, failures.size)
        if (failures.isNotEmpty()) {
            throw AssertionError(failures.entries.joinToString("\n") { (test, failure) -> "$test: ${failure.message}" })
                .apply { failures.values.forEach(::addSuppressed) }
        }
    }

}

/** Fails the run if a server game test failed. Register it after every [ServerGameTest] of the mod. */
class ResultsGameTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = GameTestResults.assertAllPassed()
}

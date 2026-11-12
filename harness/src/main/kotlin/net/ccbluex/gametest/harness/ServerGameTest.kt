package net.ccbluex.gametest.harness

import net.ccbluex.gametest.harness.connection.ControlChannel
import net.ccbluex.gametest.harness.connection.GameTestEnvironment
import net.ccbluex.gametest.harness.connection.OfflineAccount
import net.ccbluex.gametest.harness.connection.ServerSession
import net.ccbluex.gametest.harness.connection.returnToTitle
import net.ccbluex.gametest.harness.verdict.ClientErrors
import net.ccbluex.gametest.harness.verdict.ConsoleLine
import net.ccbluex.gametest.harness.verdict.Verdict
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.TitleScreen
import org.apache.logging.log4j.LogManager
import java.util.concurrent.ThreadLocalRandom
import kotlin.io.path.Path

/**
 * One game test on the game test server: the client joins under a name of its own, gets a session with fresh
 * worlds and the run's anticheats watching, and runs [body]. An attempt passes if the server and the client objected
 * no more than [violationLevel] times; a failed one is played again up to [retries] times. The class name without
 * `GameTest` is what `-Dgametest.only` selects.
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

    /** How often a failed test runs again, each time under a new name in a new session. */
    protected open val retries: Int get() = RETRIES

    /**
     * How many anticheat flags, corrections, warnings naming the player and client errors an attempt may collect and
     * still pass. A kick, a refused command or a connection lost on its own fails it at once.
     */
    protected open val violationLevel: Int get() = VIOLATION_LEVEL

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

        val attempts = retries + 1
        for (attempt in 1..attempts) {
            val failure = attempt(context, attempt) ?: return GameTestResults.passed(name, attempt)
            if (attempt == attempts) {
                GameTestResults.failed(name, failure)
            } else {
                GameTestResults.retrying(name, attempt, failure)
            }
        }
    }

    /** Plays the test once; what made it fail, or null. */
    private fun attempt(context: ClientGameTestContext, attempt: Int): Throwable? {
        val anticheats = GameTestEnvironment.anticheats
        val prefix = "GT${anticheats.map(Anticheat::letter).joinToString("")}_"
        val playerName = prefix + randomName(MAX_NAME_LENGTH - prefix.length)
        val account = context.computeOnClient<OfflineAccount, RuntimeException> {
            OfflineAccount.switch(it, playerName)
        }
        val log = if (attempt == 1) "$name.log" else "$name-$attempt.log"
        val server = ServerSession(context, Path("gametest", log))
        context.runOnClient<RuntimeException> { ControlChannel.session = server }
        ClientErrors.reset()
        return try {
            beforeJoin(context)
            val scope = GameTestScope(context, server, world, playerName, anticheats, name)
            scope.use {
                it.join()
                it.body()
            }
            judge(scope.verdict.violations(), ClientErrors.lines, server)
            null
        } catch (failure: Throwable) {
            failure
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

    private fun judge(violations: List<ConsoleLine>, clientErrors: List<String>, server: ServerSession) {
        val fatal = violations.filter { Verdict.isFatal(it) }
        check(fatal.isEmpty()) {
            "the server objected ${fatal.size} time(s) beyond any tolerance, see ${server.log}\n" +
                fatal.joinToString("\n") { "  $it" }
        }
        val level = violations.size + clientErrors.size
        check(level <= violationLevel) {
            "violation level $level above $violationLevel, see ${server.log}\n" +
                (violations.map(ConsoleLine::toString) + clientErrors).joinToString("\n") { "  $it" }
        }
        if (level > 0) {
            GameTestResults.tolerated(name, level, violations.map(ConsoleLine::toString) + clientErrors)
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

    private fun randomName(length: Int): String {
        val random = ThreadLocalRandom.current()
        return String(CharArray(length) { NAME_CHARACTERS[random.nextInt(NAME_CHARACTERS.length)] })
    }

    private companion object {
        const val STARTUP_TIMEOUT = 20 * 60 * 5
        const val MAX_NAME_LENGTH = 16
        const val NAME_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        const val RETRIES = 2
        const val VIOLATION_LEVEL = 3
    }

}

/** Collects every test's outcome so one failure does not hide the others; [ResultsGameTest] reports them. */
object GameTestResults {

    private val logger = LogManager.getLogger("GameTest")
    private val failures = linkedMapOf<String, Throwable>()
    private var passed = 0
    private var retried = 0
    private var skipped = false

    internal fun passed(test: String, attempt: Int) {
        passed++
        if (attempt > 1) {
            retried++
            logger.info("{} passed on attempt {}", test, attempt)
        } else {
            logger.info("{} passed", test)
        }
    }

    // WARN, not ERROR: the next attempt counts every ERROR the client logs against itself
    internal fun retrying(test: String, attempt: Int, failure: Throwable) {
        logger.warn("{} failed attempt {}, trying again", test, attempt, failure)
    }

    internal fun tolerated(test: String, level: Int, violations: List<String>) {
        logger.warn("{} reached violation level {}:\n{}", test, level, violations.joinToString("\n") { "  $it" })
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
        logger.info("{} server game test(s) passed ({} on a retry), {} failed", passed, retried, failures.size)
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

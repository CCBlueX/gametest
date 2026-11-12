package net.ccbluex.gametest.harness

import net.ccbluex.gametest.harness.connection.ControlChannel
import net.ccbluex.gametest.harness.connection.GameTestEnvironment
import net.ccbluex.gametest.harness.connection.OfflineAccount
import net.ccbluex.gametest.harness.connection.ServerSession
import net.ccbluex.gametest.harness.connection.returnToTitle
import net.ccbluex.gametest.harness.verdict.ClientErrors
import net.ccbluex.gametest.harness.verdict.ConsoleLine
import net.ccbluex.gametest.harness.verdict.ServerReport
import net.ccbluex.gametest.harness.verdict.Verdict
import net.ccbluex.gametest.harness.verdict.Violations
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.TitleScreen
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
 * [awaitClient], [beforeJoin] and [afterTest]. A test that no anticheat has to judge can play on the client's
 * [integrated] server instead.
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
     * How many anticheat flags, movements Paper corrected, warnings naming the player and client errors an attempt may
     * collect and still pass. Setbacks follow a flag and do not count of their own. A kick, a refused command or a
     * connection lost on its own fails the attempt at once.
     */
    protected open val violationLevel: Int get() = VIOLATION_LEVEL

    /**
     * Whether the test plays in a singleplayer world of the client's integrated server instead, for a test of what
     * the client finds, shows or keeps that no anticheat has to judge. The world is driven like a session, with the
     * same commands, but nothing watches the player, Paper's movement checks included. Such a test runs only while
     * `GAMETEST_SERVER_ANTICHEAT` is unset, and needs neither token nor address.
     */
    protected open val integrated: Boolean get() = false

    final override fun runTest(context: ClientGameTestContext) {
        // The runner fails a test that ends off the title screen, a mod's startup screen included, skipped or not
        awaitClient(context)
        if (integrated) {
            if (!GameTestEnvironment.integrated) {
                return
            }
        } else if (!GameTestEnvironment.enabled) {
            GameTestResults.skipped()
            return
        }
        if (!GameTestEnvironment.isSelected(name)) {
            return
        }

        val started = System.nanoTime()
        val attempts = retries + 1
        for (attempt in 1..attempts) {
            attempt(context, attempt)
                .onSuccess { return GameTestResults.passed(name, attempt, started, it, integrated) }
                .onFailure { failure ->
                    if (attempt == attempts) {
                        GameTestResults.failed(name, attempt, started, failure, integrated)
                    } else {
                        GameTestResults.retrying(name, attempt, failure)
                    }
                }
        }
    }

    /** Plays the test once; the violations it tolerated, or what made it fail. */
    private fun attempt(context: ClientGameTestContext, attempt: Int): Result<Violations> {
        val anticheats = if (integrated) emptySet() else GameTestEnvironment.anticheats
        val prefix = "GT${anticheats.map(Anticheat::letter).joinToString("")}_"
        val playerName = prefix + randomName(MAX_NAME_LENGTH - prefix.length)
        val account = context.computeOnClient<OfflineAccount, RuntimeException> {
            OfflineAccount.switch(it, playerName)
        }
        val log = if (attempt == 1) "$name.log" else "$name-$attempt.log"
        val server = ServerSession(context, Path("gametest", log), integrated)
        context.runOnClient<RuntimeException> { ControlChannel.session = server }
        ClientErrors.reset()
        return try {
            beforeJoin(context)
            val scope = GameTestScope(context, server, world, playerName, anticheats, name)
            scope.use {
                it.join()
                it.body()
            }
            Result.success(judge(scope.verdict.violations(), ClientErrors.lines, server))
        } catch (failure: Throwable) {
            Result.failure(failure)
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

    private fun judge(violations: List<ConsoleLine>, clientErrors: List<String>, server: ServerSession): Violations {
        val fatal = violations.filter { Verdict.isFatal(it) }
        check(fatal.isEmpty()) {
            "the server objected ${fatal.size} time(s) beyond any tolerance, see ${server.log}\n" +
                fatal.joinToString("\n") { "  $it" }
        }
        val level = violations.count { it.report != ServerReport.SETBACK } + clientErrors.size
        val all = violations.map(ConsoleLine::toString) + clientErrors
        check(level <= violationLevel) {
            "violation level $level above $violationLevel, see ${server.log}\n" + all.joinToString("\n") { "  $it" }
        }
        return Violations(level, all)
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
        const val VIOLATION_LEVEL = 5
    }

}

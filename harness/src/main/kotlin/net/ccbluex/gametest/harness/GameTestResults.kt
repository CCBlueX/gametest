package net.ccbluex.gametest.harness

import net.ccbluex.gametest.harness.connection.GameTestEnvironment
import net.ccbluex.gametest.harness.report.Console
import net.ccbluex.gametest.harness.report.Outcome
import net.ccbluex.gametest.harness.verdict.Violations
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

/** Collects every test's outcome so one failure does not hide the others; [ResultsGameTest] reports them. */
object GameTestResults {

    private val outcomes = mutableListOf<Outcome>()
    private var skipped = false
    private var announced = false

    private const val CLIENT_TESTS = "Client game tests"

    internal fun passed(test: String, attempts: Int, started: Long, violations: Violations, singleplayer: Boolean) =
        record(Outcome(test, attempts, System.nanoTime() - started, violations, null), singleplayer)

    internal fun retrying(test: String, attempt: Int, failure: Throwable) = Console.retrying(test, attempt, failure)

    internal fun failed(test: String, attempts: Int, started: Long, failure: Throwable, singleplayer: Boolean) =
        record(Outcome(test, attempts, System.nanoTime() - started, Violations.NONE, failure), singleplayer)

    internal fun skipped() {
        if (!skipped) {
            skipped = true
            Console.note("Skipping the server game tests: neither GAMETEST_API_TOKEN nor GAMETEST_SERVER_ADDRESS set")
        }
    }

    /** Records a [ClientGameTest] that ran once, failed if it threw [failure]. */
    internal fun ran(test: String, started: Long, failure: Throwable?) =
        record(Outcome(test, 1, System.nanoTime() - started, Violations.NONE, failure), true)

    private fun record(outcome: Outcome, client: Boolean) {
        announce(
            if (client) {
                CLIENT_TESTS
            } else {
                "Server game tests on ${GameTestEnvironment.address}, watched by " +
                    GameTestEnvironment.anticheats.joinToString { it.name.lowercase() }
            }
        )
        outcomes += outcome
        Console.outcome(outcome)
    }

    private fun announce(header: String) {
        if (!announced) {
            announced = true
            Console.header(header)
        }
    }

    fun assertAllPassed() {
        if (outcomes.isEmpty()) {
            return
        }
        Console.summary(outcomes)
        val failures = outcomes.filter { it.failure != null }
        if (failures.isNotEmpty()) {
            throw AssertionError(failures.joinToString("\n") { "${it.test}: ${it.failure?.message}" })
                .apply { failures.forEach { addSuppressed(it.failure) } }
        }
    }

}

/** Fails the run if a game test failed. Register it after every game test of the mod. */
class ResultsGameTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = GameTestResults.assertAllPassed()
}

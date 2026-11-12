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

    internal fun passed(test: String, attempts: Int, started: Long, violations: Violations, integrated: Boolean) =
        record(Outcome(test, attempts, System.nanoTime() - started, violations, null), integrated)

    internal fun retrying(test: String, attempt: Int, failure: Throwable) = Console.retrying(test, attempt, failure)

    internal fun failed(test: String, attempts: Int, started: Long, failure: Throwable, integrated: Boolean) =
        record(Outcome(test, attempts, System.nanoTime() - started, Violations.NONE, failure), integrated)

    internal fun skipped() {
        if (!skipped) {
            skipped = true
            Console.note("Skipping the server game tests: neither GAMETEST_API_TOKEN nor GAMETEST_SERVER_ADDRESS set")
        }
    }

    /**
     * Runs [block] as the game test [test] of the client alone and reports it like a server test. Such a test fails
     * the run on its own, so the failure is thrown on.
     */
    fun local(test: String, block: () -> Unit) {
        announce(CLIENT_TESTS)
        val started = System.nanoTime()
        try {
            block()
        } catch (failure: Throwable) {
            record(Outcome(test, 1, System.nanoTime() - started, Violations.NONE, failure), true)
            throw failure
        }
        record(Outcome(test, 1, System.nanoTime() - started, Violations.NONE, null), true)
    }

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

/** Fails the run if a server game test failed. Register it after every [ServerGameTest] of the mod. */
class ResultsGameTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = GameTestResults.assertAllPassed()
}

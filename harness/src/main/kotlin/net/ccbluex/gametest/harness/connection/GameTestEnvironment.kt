package net.ccbluex.gametest.harness.connection

import net.ccbluex.gametest.harness.Anticheat

/** Where the game test server is and how to get in, read from the environment of the test run. */
object GameTestEnvironment {

    private const val PUBLIC_SERVER = "test.ccbluex.net"

    /** The LiquidBounce API token that the public server's bot tickets are asked for with. */
    internal val token: String? = variable("GAMETEST_API_TOKEN")

    private val ownServer: String? = variable("GAMETEST_SERVER_ADDRESS")

    /** `host[:port]` of the server. */
    val address: String = ownServer ?: PUBLIC_SERVER

    /** What watches the bots of this run. */
    val anticheat: Anticheat = variable("GAMETEST_SERVER_ANTICHEAT")?.let(Anticheat::of) ?: Anticheat.GRIM

    /** Server game tests run with a token for the public server, or against a server of one's own. */
    val enabled = token != null || ownServer != null

    /** Shown to spectators next to a running test. */
    internal val repository: String? = variable("GITHUB_REPOSITORY")

    internal val run: String? = variable("GITHUB_RUN_ID")

    private val selection = System.getProperty("gametest.only")
        ?.split(',')
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.toSet()

    /** Whether [test], a class name without `GameTest`, runs: all do unless `-Dgametest.only` names some. */
    fun isSelected(test: String) = selection == null || test in selection

    private fun variable(name: String) = System.getenv(name)?.takeIf(String::isNotBlank)

}

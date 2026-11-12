package net.ccbluex.gametest.harness.connection

import com.google.gson.JsonObject
import net.ccbluex.gametest.harness.GameTestScope
import net.ccbluex.gametest.harness.TestWorld
import net.ccbluex.gametest.harness.verdict.ServerReport
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.DisconnectedScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import org.apache.logging.log4j.LogManager
import java.util.concurrent.TimeUnit

private const val JOIN_TIMEOUT = 20 * 60
private const val OPEN_TIMEOUT = 20 * 60
private const val RETRY_TICKS = 100
private const val BUSY_PATIENCE = 180
private const val BOT_LIMIT_PATIENCE = 600

// How the proxy turns away a bot whose token already runs as many bots as it may
private const val BOT_LIMIT = "wait for one to finish"

/** Back to the title screen, from a server the way the pause menu leaves it. */
internal fun Minecraft.returnToTitle() {
    val level = level
    if (level != null) {
        // Minecraft.disconnect alone drops the world but keeps the connection
        level.disconnect(ClientLevel.DEFAULT_QUIT_MESSAGE)
        disconnect(TitleScreen(), false)
    } else if (gui.screen() !is TitleScreen) {
        gui.setScreen(TitleScreen())
    }
}

/** The way of [scope]'s client onto the game test server, into its session there, and out again. */
internal class ServerEntrance(
    private val scope: GameTestScope,
    private val world: TestWorld,
    private val test: String,
) {

    private val logger = LogManager.getLogger("GameTest")
    private val server = scope.server

    /**
     * Joins the server, trying again for [patience] seconds while it refuses, and for [BOT_LIMIT_PATIENCE] while
     * the token's other bots fill its share of the server.
     */
    fun connect(patience: Int) {
        val start = System.nanoTime()
        while (true) {
            val refusal = attempt() ?: return
            val waited = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start)
            if (waited > if (BOT_LIMIT in refusal) BOT_LIMIT_PATIENCE else patience) {
                throw AssertionError("Could not join ${server.address}: $refusal")
            }
            logger.info("Could not join {} yet, trying again: {}", server.address, refusal)
            scope.ticks(RETRY_TICKS)
        }
    }

    fun leave() {
        scope.client { it.returnToTitle() }
        scope.awaitClient("the client to leave") { it.level == null && it.gui.screen() is TitleScreen }
    }

    /** Returns what the disconnect screen says if the client did not get in. */
    private fun attempt(): String? {
        scope.client { minecraft ->
            ConnectScreen.startConnecting(
                TitleScreen(), minecraft, ServerAddress.parseString(server.address),
                ServerData("Game test server", server.address, ServerData.Type.OTHER), false, null,
            )
        }
        scope.awaitClient("an answer from ${server.address}", JOIN_TIMEOUT) {
            it.player != null && it.level != null || it.gui.screen() is DisconnectedScreen
        }
        return scope.client { (it.gui.screen() as? DisconnectedScreen)?.narrationMessage?.string }
    }

    /** Asks for the session until the server has room for it, and returns the server's `ready`. */
    fun open(): JsonObject {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(BUSY_PATIENCE.toLong())
        while (true) {
            val answer = ask()
            if (answer["type"].asString == "ready") {
                logger.info(
                    "{} plays as {} in session {} on {} with {}", test, scope.playerName,
                    answer["session"].asString, answer["server"].asString, answer["anticheat"].asString,
                )
                return answer
            }
            if (System.nanoTime() > deadline) {
                throw AssertionError("${server.address} stayed busy for $BUSY_PATIENCE seconds")
            }
            scope.ticks(answer["retryAfter"].asInt.coerceIn(1, 60) * 20)
        }
    }

    private fun ask(): JsonObject {
        val since = server.console.size
        server.answer = null
        server.send("open") {
            add("world", world.generation)
            add("info", JsonObject().apply {
                GameTestEnvironment.repository?.let { addProperty("repository", it) }
                GameTestEnvironment.run?.let { addProperty("run", it) }
                addProperty("test", test)
            })
        }
        repeat(OPEN_TIMEOUT) {
            server.answer?.let { return it }
            val refusal = server.console.drop(since).firstOrNull { it.report == ServerReport.ERROR }
            check(refusal == null && server.connected) {
                "${server.address} did not open a session: ${refusal?.message ?: "the connection ended"}"
            }
            scope.ticks(1)
        }
        throw AssertionError("${server.address} did not answer the request for a session, see ${server.log}")
    }

}

package net.ccbluex.gametest.harness.connection

import net.ccbluex.gametest.harness.GameTestScope
import net.ccbluex.gametest.harness.TestWorld
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property

/**
 * The way of [scope]'s client into a singleplayer world of its own, and out again. The world is the session: it is
 * created on the first join and opened again after a leave.
 */
internal class SingleplayerEntrance(private val scope: GameTestScope, private val world: TestWorld) : Entrance {

    private val server = scope.server
    private var save: TestWorldSave? = null
    private var singleplayer: TestSingleplayerContext? = null

    override fun connect(resume: Boolean) {
        val opened = save?.open() ?: scope.context.worldBuilder().adjustSettings(world::configure).create()
        save = opened.worldSave
        singleplayer = opened
        server.integratedServer = opened.server
        IntegratedServerLog.watched = server to scope.playerName
    }

    override fun open(resume: Boolean) = Unit

    override fun end() = Unit

    override fun drop() = leave()

    override fun leave() {
        val opened = singleplayer ?: return
        singleplayer = null
        server.integratedServer = null
        opened.close()
        IntegratedServerLog.watched = null
        server.disconnected()
    }

}

/** Hands the lines the integrated server logs about the player to its session, as the game test server does. */
private object IntegratedServerLog : AbstractAppender(
    "GameTestIntegratedServerLog", null, null, true, Property.EMPTY_ARRAY,
) {

    private const val SERVER_THREAD = "Server thread"

    @Volatile
    var watched: Pair<ServerSession, String>? = null

    init {
        start()
        val context = LogManager.getContext(false) as LoggerContext
        context.configuration.rootLogger.addAppender(this, Level.INFO, null)
        context.updateLoggers()
    }

    override fun append(event: LogEvent) {
        val (session, player) = watched ?: return
        val message = event.message.formattedMessage
        if (event.threadName != SERVER_THREAD || player !in message) {
            return
        }
        val level = when {
            event.level.isMoreSpecificThan(Level.ERROR) -> "ERROR"
            event.level == Level.WARN -> "WARN"
            else -> "INFO"
        }
        session.log(level, event.thrown?.let { "$message\n$it" } ?: message)
    }

}

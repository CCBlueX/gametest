package net.ccbluex.gametest.harness.verdict

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The ERRORs the client logs, which count against a test, apart from what a test machine without an account, sound
 * or graphics card causes anyway. A mod adds its own noise with [ignore].
 */
object ClientErrors : AbstractAppender("GameTestClientErrors", null, null, true, Property.EMPTY_ARRAY) {

    private val environment = CopyOnWriteArrayList(
        listOf(
            "Failed to fetch", "profile key", "realms", "Realms", "[com.mojang.blaze3d.platform.Window]",
            "Anisotropic", "narrator",
        )
    )

    private val logged = CopyOnWriteArrayList<String>()

    internal val lines: List<String> get() = logged.toList()

    init {
        start()
        val context = LogManager.getContext(false) as LoggerContext
        context.configuration.rootLogger.addAppender(this, Level.ERROR, null)
        context.updateLoggers()
    }

    /** Errors whose line contains one of [fragments] do not count. */
    fun ignore(vararg fragments: String) {
        environment += fragments
    }

    internal fun reset() = logged.clear()

    override fun append(event: LogEvent) {
        val line = "[${event.loggerName}] ${event.message.formattedMessage}" + (event.thrown?.let { ": $it" } ?: "")
        if (environment.none { it in line }) {
            logged += line
        }
    }

}

package net.ccbluex.gametest.harness.verdict

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Every ERROR the client logs fails the test, apart from what a test machine without an account, sound or
 * graphics card causes anyway. A mod adds its own noise with [ignore].
 */
object ClientErrors : AbstractAppender("GameTestClientErrors", null, null, true, Property.EMPTY_ARRAY) {

    private val environment = CopyOnWriteArrayList(
        listOf(
            "Failed to fetch", "profile key", "realms", "Realms", "[com.mojang.blaze3d.platform.Window]",
            "Anisotropic", "narrator",
        )
    )

    private val lines = CopyOnWriteArrayList<String>()

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

    internal fun reset() = lines.clear()

    internal fun assertNone() = check(lines.isEmpty()) {
        "the client logged ${lines.size} error(s), first: ${lines.first()}"
    }

    override fun append(event: LogEvent) {
        val line = "[${event.loggerName}] ${event.message.formattedMessage}" + (event.thrown?.let { ": $it" } ?: "")
        if (environment.none { it in line }) {
            lines += line
        }
    }

}

package net.ccbluex.gametest.harness.report

import net.ccbluex.gametest.harness.GameTestResults
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.Filter
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.ConsoleAppender
import org.apache.logging.log4j.core.filter.AbstractFilter

/**
 * Keeps the report's own lines off the console appenders, which [Console] prints itself, and when [Console.quiet]
 * everything else too but what ends the run: a fatal error or a failure of the game test runner.
 */
class ConsoleFilter : PreLaunchEntrypoint {

    override fun onPreLaunch() {
        val context = LogManager.getContext(false) as LoggerContext
        context.configuration.appenders.values.filterIsInstance<ConsoleAppender>().forEach { it.addFilter(Rule) }
    }

    private object Rule : AbstractFilter(Filter.Result.NEUTRAL, Filter.Result.DENY) {

        override fun filter(event: LogEvent): Filter.Result = when {
            event.marker?.isInstanceOf(Console.REPORT) == true -> Filter.Result.DENY
            !Console.quiet -> Filter.Result.NEUTRAL
            event.level.isMoreSpecificThan(Level.FATAL) -> Filter.Result.NEUTRAL
            event.level.isMoreSpecificThan(Level.ERROR) && event.loggerName.startsWith(RUNNER) &&
                !reported(event.thrown) -> Filter.Result.NEUTRAL
            else -> Filter.Result.DENY
        }

        // The summary has listed these failures already
        private fun reported(thrown: Throwable?) =
            thrown?.stackTrace?.any { it.className == GameTestResults::class.java.name } == true

    }

}

private const val RUNNER = "fabric-client-gametest"

package net.ccbluex.gametest.harness.verdict

import net.ccbluex.gametest.harness.connection.ServerSession

/**
 * Everything the server objected to: an anticheat flag or setback, a movement Paper corrected, a kick, a refused
 * command, a lost connection the test did not ask for, or a warning or error in its log that names the player.
 */
class Verdict(private val server: ServerSession) {

    private val excused = mutableListOf<IntRange>()
    private val excusedCorrections = mutableListOf<IntRange>()
    private val departures = mutableListOf<IntRange>()

    fun excuse(lines: IntRange) {
        excused += lines
    }

    /** Setbacks and corrected movements between these lines, but nothing else. */
    fun excuseCorrections(lines: IntRange) {
        excusedCorrections += lines
    }

    /** Between these console lines, the player left on purpose. */
    fun departed(lines: IntRange) {
        departures += lines
    }

    fun violations() = server.console.withIndex()
        .filter { (index, line) -> !isExcused(line, index) && isViolation(line, departures.any { index in it }) }
        .map { it.value }

    fun isViolation(line: ConsoleLine, leaving: Boolean = false) = when (line.report) {
        ServerReport.FLAG, ServerReport.SETBACK, ServerReport.FAILMOVE, ServerReport.KICK, ServerReport.ERROR -> true
        ServerReport.QUIT -> !leaving
        ServerReport.NOTE -> false
        null -> line.level == "WARN" || line.level == "ERROR"
    }

    private fun isExcused(line: ConsoleLine, index: Int) = excused.any { index in it } ||
        (line.report == ServerReport.SETBACK || line.report == ServerReport.FAILMOVE) &&
        excusedCorrections.any { index in it }

}

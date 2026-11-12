package net.ccbluex.gametest.harness.verdict

/**
 * One thing the server said about the session: a feedback line of [command], a [report], or a line of its
 * log at [level] that names the player. [anticheat] names the anticheat behind a FLAG, NOTE or SETBACK.
 */
@JvmRecord
data class ConsoleLine(
    val level: String,
    val message: String,
    val report: ServerReport? = null,
    val command: Int? = null,
    val anticheat: String? = null,
) {

    override fun toString() = when {
        anticheat != null -> "$report $anticheat $message"
        report != null -> "$report $message"
        command != null -> "#$command $message"
        else -> "[$level] $message"
    }

}

/**
 * What the server reports about the player by itself: an anticheat flag or setback, a movement Paper corrected,
 * a kick, a refused command or misused protocol, and, noticed by the client, the end of the connection. A NOTE is a
 * flag of a check the anticheat calls experimental; it is only logged as a warning.
 */
enum class ServerReport { FLAG, NOTE, SETBACK, FAILMOVE, KICK, ERROR, QUIT }

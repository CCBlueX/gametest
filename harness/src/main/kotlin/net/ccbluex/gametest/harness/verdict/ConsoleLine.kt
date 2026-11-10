package net.ccbluex.gametest.harness.verdict

/**
 * One thing the server said about the session: a feedback line of [command], a [report], or a line of its
 * log at [level] that names the player.
 */
@JvmRecord
data class ConsoleLine(
    val level: String,
    val message: String,
    val report: ServerReport? = null,
    val command: Int? = null,
) {

    override fun toString() = when {
        report != null -> "$report $message"
        command != null -> "#$command $message"
        else -> "[$level] $message"
    }

}

/**
 * What the server reports about the player by itself: an anticheat flag or setback, a movement Paper corrected,
 * a kick, a refused command or misused protocol, and, noticed by the client, the end of the connection.
 */
enum class ServerReport { FLAG, SETBACK, FAILMOVE, KICK, ERROR, QUIT }

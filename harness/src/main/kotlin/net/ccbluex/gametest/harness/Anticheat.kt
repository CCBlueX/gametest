package net.ccbluex.gametest.harness

/**
 * What watches the bot on the server, see docs/anticheats.md. The server installs them side by side and reads the
 * bot's choice from the letter after `GT` in its name.
 */
enum class Anticheat(
    internal val letter: Char,
    private val serverName: String,
    /**
     * How long after a join the anticheat starts to check the player. Until then a test would pass for nothing, so
     * the harness waits that long before the test goes on.
     */
    internal val warmupTicks: Int,
    /** The same after a teleport, on top of the second [GameTestScope.travel] waits anyway. */
    internal val teleportGraceTicks: Int,
) {

    VANILLA('V', "Vanilla", 0, 0),
    GRIM('G', "GrimAC", 0, 0),
    // Measured: a fake move 4 s after the join passes Intave, one after 7 s does not
    INTAVE('I', "Intave", 8 * 20, 0),
    // Measured: a fake move 1.5 s after a teleport passes Vulcan, one after 2.5 s does not
    VULCAN('U', "Vulcan", 0, 2 * 20),
    // Measured: a fake move 1.5 s after the join passes Matrix, one after 3 s does not
    MATRIX('M', "Matrix", 4 * 20, 0),
    // Measured: a fake move 0.3 s after the join passes Spartan, one after 1.5 s does not
    SPARTAN('S', "Spartan", 2 * 20, 0),
    MX('X', "MX", 0, 0),
    ANTICHEATADDITION('A', "AntiCheatAddition", 0, 0),
    FAIRPLAY('F', "FairPlay", 0, 0);

    /** Whether the server's description of its anticheat, as in `GrimAC 2.3.75`, names this one. */
    internal fun describes(description: String) = description.substringBefore(' ') == serverName

    companion object {

        fun of(name: String) = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: throw IllegalArgumentException("Unknown anticheat $name, one of ${entries.joinToString()}")

    }

}

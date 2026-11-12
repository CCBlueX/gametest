package net.ccbluex.gametest.harness

import net.ccbluex.gametest.harness.action.teleport
import net.ccbluex.gametest.harness.connection.Entrance
import net.ccbluex.gametest.harness.connection.ServerEntrance
import net.ccbluex.gametest.harness.connection.ServerSession
import net.ccbluex.gametest.harness.connection.SingleplayerEntrance
import net.ccbluex.gametest.harness.verdict.ConsoleLine
import net.ccbluex.gametest.harness.verdict.ServerReport
import net.ccbluex.gametest.harness.verdict.Verdict
import net.fabricmc.fabric.api.client.gametest.v1.TestInput
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level

/**
 * What a server game test works with: the client, driven through real input, and its session on the game test
 * server, driven through the session's console like an admin would. The server's state is the truth; the client
 * only shows its prediction. Actions on either side live in the `action` package. A test that asked for the
 * integrated server gets a singleplayer world instead, driven the same way and watched by nothing.
 */
@Suppress("TooManyFunctions")
class GameTestScope internal constructor(
    val context: ClientGameTestContext,
    val server: ServerSession,
    private val world: TestWorld,
    /** The name this test plays under, a fresh one for every test. */
    val playerName: String,
    /**
     * What watches the player, so that a test can expect what only some anticheats catch. Nothing does on the
     * integrated server.
     */
    val anticheats: Set<Anticheat>,
    test: String,
) : AutoCloseable {

    /** Whether Paper's own movement checks are all that watch the player. */
    val paperOnly = anticheats.singleOrNull() == Anticheat.VANILLA

    /** Where the test starts, looking south: the grass of the superflat world unless it asked for another. */
    val origin = world.origin

    val input: TestInput get() = context.input

    internal val verdict = Verdict(server)

    private val warmupTicks = anticheats.maxOfOrNull { it.warmupTicks } ?: 0
    private val teleportGraceTicks = anticheats.maxOfOrNull { it.teleportGraceTicks } ?: 0

    private val entrance: Entrance =
        if (server.integrated) SingleplayerEntrance(this, world) else ServerEntrance(this, world, test)
    private var prepared = false
    private var joined = false

    fun <T> client(action: (Minecraft) -> T): T = context.computeOnClient<T, RuntimeException>(action)

    fun ticks(count: Int) = context.waitTicks(count)

    fun awaitClient(what: String, timeout: Int = DEFAULT_TIMEOUT, condition: (Minecraft) -> Boolean) {
        try {
            context.waitFor(condition, timeout)
        } catch (failure: AssertionError) {
            throw AssertionError("Timed out after $timeout ticks waiting for $what", failure)
        }
    }

    /** Runs [command] as the session's console; not to be called from inside [client]. */
    fun run(command: String) = server.run(command)

    /** Ticks until a console line from [since] on [matches]; the client must never stand still meanwhile. */
    fun awaitConsole(
        what: String,
        since: Int = 0,
        timeout: Int = DEFAULT_TIMEOUT,
        matches: (ConsoleLine) -> Boolean,
    ): ConsoleLine {
        repeat(timeout) {
            server.console.drop(since).firstOrNull(matches)?.let { return it }
            check(!joined || server.connected) {
                "The client lost its connection while waiting for $what, see ${server.log}"
            }
            context.waitTick()
        }
        throw AssertionError("Timed out after $timeout ticks waiting for $what, see ${server.log}")
    }

    /**
     * A name no other test on the server uses. Scoreboard objectives, teams, bossbars and command storage
     * are shared by every session, so tests name theirs through this.
     */
    fun unique(name: String) = "${name}_${playerName.substringAfter('_').lowercase()}"

    /**
     * Teleports and waits until the client stands there with the area rendered and the anticheat watches again. An
     * anticheat may hold a player in chunks the client has not received yet by setting them back, and after a change
     * of dimension the client may move before its chunk is there, which Paper corrects; both belong to the trip.
     */
    fun travel(pos: BlockPos, yaw: Float = 0f, pitch: Float = 0f, dimension: ResourceKey<Level>? = null) {
        val since = server.console.size
        teleport(pos, yaw, pitch, dimension)
        arrive(pos, dimension, since)
    }

    /** Waits for the trip that started at console line [since] to end at [pos]. */
    private fun arrive(pos: BlockPos, dimension: ResourceKey<Level>?, since: Int) {
        // A player in a chunk the client does not have yet keeps its last on-ground state, and there is
        // nothing to render yet either, so the chunk itself has to be there first
        awaitClient("the player to arrive at $pos") { it.hasArrived(pos, dimension) }
        // The anticheat lets go only once it has seen the client confirm the chunk, which can take a few round trips;
        // after a correction, the next one may come more than a second later
        var window = 20
        repeat(SETTLE_ATTEMPTS) {
            val checked = server.console.size
            ticks(window)
            if (server.console.drop(checked).none { it.report in CORRECTIONS }) {
                verdict.excuseCorrections(since until checked)
                if (teleportGraceTicks > 0) {
                    ticks(teleportGraceTicks)
                }
                return
            }
            window = 40
        }
        throw AssertionError("The server kept correcting the player at $pos, see ${server.log}")
    }

    /**
     * The negative control: [action] must make the server object at least once. Those objections do not
     * count against the test.
     */
    fun expectViolation(what: String, action: () -> Unit) {
        val since = server.console.size
        action()
        awaitConsole(what, since) { verdict.isViolation(it) }
        ticks(40)
        verdict.excuse(since until server.console.size)
    }

    /** Leaves like a player would and comes back into the same session, at [origin]. */
    fun reconnect() {
        leave()
        join()
    }

    /**
     * Has the server disconnect the player the way a stopping server does, which is as close as a test on a
     * shared server gets to `stop`. The client is left on its disconnect screen and the session waits a
     * minute for [join]. On the integrated server the world closes and [join] opens it again.
     */
    fun drop() {
        val since = server.console.size
        joined = false
        entrance.drop()
        awaitConsole("the server to drop the player", since) { it.report == ServerReport.QUIT }
        // The connection ends on a network thread, the game notices on its next tick
        awaitClient("the client to notice the drop") { it.level == null }
        verdict.excuse(since until server.console.size)
    }

    /** Connects and enters the session at [origin]: a new one first, the one left behind afterwards. */
    fun join() {
        entrance.connect(prepared)
        val connected = System.nanoTime()
        joined = true
        // Opening the session moves the player out of the lobby, the first leg of the trip to the origin
        val since = server.console.size
        entrance.open(prepared)
        if (!prepared) {
            GAME_RULES.forEach { run("gamerule $it") }
            run("time set day")
            world.prepare(this)
            prepared = true
        }
        // The lobby is a dimension of its own, so the session's overworld tells the two apart
        teleport(origin, 0f, 0f, Level.OVERWORLD)
        arrive(origin, Level.OVERWORLD, since)
        val elapsedTicks = (System.nanoTime() - connected) / NANOS_PER_TICK
        if (warmupTicks > elapsedTicks) {
            ticks((warmupTicks - elapsedTicks).toInt())
        }
    }

    /** Ends the session and leaves the server like a player would, so that the quit is expected. */
    override fun close() {
        if (joined) {
            ticks(20)
            entrance.end()
        }
        leave()
    }

    /** Leaves like a player would; the session waits a minute for [join]. */
    fun leave() {
        val since = server.console.size
        val connected = joined && server.connected
        joined = false
        // A server drops what a player who is gone still had queued, so the quit waits for every answer
        var waited = 0
        while (connected && server.connected && server.hasPendingCommands && waited++ < COMMAND_TIMEOUT) {
            ticks(1)
        }
        entrance.leave()
        if (connected) {
            awaitConsole("the connection to end", since) { it.report == ServerReport.QUIT }
            verdict.departed(since until server.console.size)
        }
    }

    private fun Minecraft.hasArrived(pos: BlockPos, dimension: ResourceKey<Level>?): Boolean {
        val player = player ?: return false
        val level = level ?: return false
        if (dimension != null && level.dimension() != dimension) {
            return false
        }
        return level.hasChunkAt(pos) && player.blockPosition() == pos && player.onGround() &&
            levelRenderer.hasRenderedAllSections()
    }

    companion object {
        const val DEFAULT_TIMEOUT = 200
        internal const val SETTLE_ATTEMPTS = 10
        private val CORRECTIONS = setOf(ServerReport.SETBACK, ServerReport.FAILMOVE)
        internal const val COMMAND_TIMEOUT = 100
        internal const val NANOS_PER_TICK = 50_000_000L
        internal val GAME_RULES = listOf(
            "minecraft:advance_time false",
            "minecraft:advance_weather false",
            "minecraft:spawn_mobs false",
            "minecraft:random_tick_speed 0",
            "minecraft:show_advancement_messages false",
            "minecraft:respawn_radius 0",
        )
    }

}

package net.ccbluex.gametest.harness.action

import net.ccbluex.gametest.harness.GameTestScope
import net.ccbluex.gametest.harness.GameTestScope.Companion.DEFAULT_TIMEOUT
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level

private const val RETRY_INTERVAL = 10

/** How long a stalled server may take to answer the last question before it counts as a no. */
private const val ANSWER_PATIENCE = 200

private val BlockPos.command get() = "$x $y $z"

fun GameTestScope.setBlock(pos: BlockPos, block: String) = run("setblock ${pos.command} $block")

fun GameTestScope.fill(from: BlockPos, to: BlockPos, block: String) = run("fill ${from.command} ${to.command} $block")

fun GameTestScope.summon(entity: String, pos: BlockPos, nbt: String = "") =
    run("summon $entity ${pos.x + 0.5} ${pos.y} ${pos.z + 0.5} $nbt".trimEnd())

fun GameTestScope.give(item: String, count: Int = 1) = run("give $playerName $item $count")

fun GameTestScope.teleport(
    pos: BlockPos,
    yaw: Float = 0f,
    pitch: Float = 0f,
    dimension: ResourceKey<Level>? = null,
) {
    val teleport = "tp $playerName ${pos.x + 0.5} ${pos.y} ${pos.z + 0.5} $yaw $pitch"
    run(if (dimension == null) teleport else "execute in ${dimension.identifier()} run $teleport")
}

fun GameTestScope.forceLoad(chunk: ChunkPos, load: Boolean = true) =
    run("forceload ${if (load) "add" else "remove"} ${chunk.minBlockX} ${chunk.minBlockZ}")

fun GameTestScope.hangItemFrame(pos: BlockPos, facing: Direction, item: String) {
    setBlock(pos.relative(facing.opposite), "minecraft:stone")
    // Vanilla checks block_pos against the entity's position while reading the NBT, before the summon
    // command moves it, so the position has to be in there as well
    val position = "Pos:[${pos.x + 0.5}d,${pos.y}.0d,${pos.z + 0.5}d]"
    val blockPos = "block_pos:[I;${pos.x},${pos.y},${pos.z}]"
    val nbt = """{$position,$blockPos,Facing:${facing.get3DDataValue()}b,Item:{id:"$item",count:1}}"""
    summon("minecraft:item_frame", pos, nbt)
}

/** Runs [command] and returns the first line of its feedback that matches [answer]. */
fun GameTestScope.query(command: String, answer: Regex, timeout: Int = DEFAULT_TIMEOUT): MatchResult {
    val id = run(command)
    val line = awaitConsole("an answer to '$command'", timeout = timeout) {
        it.command == id && answer.containsMatchIn(it.message)
    }
    return checkNotNull(answer.find(line.message))
}

/** Waits until the server confirms `execute if [condition]`. */
fun GameTestScope.awaitServer(condition: String, timeout: Int = DEFAULT_TIMEOUT) =
    awaitAnswer("execute if $condition", "Test passed", timeout)

/**
 * Asks the server [command] every half second until its feedback contains [expected]. Questions are judged by the
 * server's answer, however late it comes: a server that stalls for a second answers them all at once.
 */
fun GameTestScope.awaitAnswer(command: String, expected: String, timeout: Int = DEFAULT_TIMEOUT) {
    val asked = HashSet<Int>()
    fun answered() = server.console.any { it.command in asked && expected in it.message }
    repeat(maxOf(1, timeout / RETRY_INTERVAL)) {
        asked += run(command)
        ticks(RETRY_INTERVAL)
        if (answered()) {
            return
        }
    }
    val last = asked.max()
    for (tick in 0 until ANSWER_PATIENCE) {
        if (server.result(last) != null) {
            break
        }
        ticks(1)
    }
    if (!answered()) {
        throw AssertionError("The server never answered '$command' with '$expected', see ${server.log}")
    }
}

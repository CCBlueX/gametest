package net.ccbluex.gametest.harness

import com.google.gson.JsonObject
import net.ccbluex.gametest.harness.action.awaitServer
import net.ccbluex.gametest.harness.action.fill
import net.minecraft.core.BlockPos

/**
 * The world a test plays in: what the server generates for its session, where the player starts and what
 * the server builds there before the player arrives.
 */
class TestWorld private constructor(
    internal val generation: JsonObject,
    val origin: BlockPos,
    internal val prepare: GameTestScope.() -> Unit = {},
) {

    companion object {

        private const val FLAT_LAYERS = """{"biome":"minecraft:plains","layers":[""" +
            """{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},""" +
            """{"block":"minecraft:grass_block","height":1}]}"""

        /** Superflat plains, the grass at y -61. What every test gets unless it asks for more. */
        val FLAT = TestWorld(
            generation("flat") { addProperty("generatorSettings", FLAT_LAYERS) },
            BlockPos(0, -60, 0),
        )

        /** Nothing but a stone platform of 9 by 9 blocks around the player. */
        val VOID = TestWorld(generation("void"), BlockPos(0, 64, 0)) {
            fill(BlockPos(-4, 63, -4), BlockPos(4, 63, 4), "minecraft:stone")
            awaitServer("block 0 63 0 minecraft:stone")
        }

        /**
         * A default world from [seed]. The player starts in a glass room at y -40, deep enough for every
         * overworld ore and sealed against the caves and aquifers around it.
         */
        fun generated(seed: Long, structures: Boolean = false) = TestWorld(
            generation("normal") {
                addProperty("seed", seed.toString())
                addProperty("structures", structures)
            },
            BlockPos(0, -40, 0),
        ) {
            run("forceload add -3 -3 3 3")
            awaitServer("loaded 0 -40 0")
            fill(BlockPos(-3, -41, -3), BlockPos(3, -36, 3), "minecraft:glass hollow")
            awaitServer("block 0 -41 0 minecraft:glass")
        }

        private fun generation(type: String, settings: JsonObject.() -> Unit = {}) =
            JsonObject().apply { addProperty("type", type) }.apply(settings)

    }

}

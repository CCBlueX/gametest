package net.ccbluex.gametest.harness

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.ccbluex.gametest.harness.action.awaitServer
import net.ccbluex.gametest.harness.action.fill
import net.minecraft.client.gui.screens.worldselection.PresetEditor
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.RegistryOps
import net.minecraft.world.Difficulty
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings
import net.minecraft.world.level.levelgen.presets.WorldPresets

/**
 * The world a test plays in: what the server generates for its session, where the player starts and what
 * the server builds there before the player arrives.
 */
class TestWorld private constructor(
    internal val generation: JsonObject,
    val origin: BlockPos,
    internal val prepare: GameTestScope.() -> Unit = {},
) {

    /** Makes a singleplayer world the integrated server generates like the game test server does this one. */
    internal fun configure(state: WorldCreationUiState) {
        val registries = state.settings.worldgenLoadContext()
        val presets = registries.lookupOrThrow(Registries.WORLD_PRESET)
        if (generation["type"].asString == "normal") {
            state.worldType = WorldCreationUiState.WorldTypeEntry(presets.getOrThrow(WorldPresets.NORMAL))
            state.seed = generation["seed"].asString
            state.isGenerateStructures = generation["structures"].asBoolean
        } else {
            state.worldType = WorldCreationUiState.WorldTypeEntry(presets.getOrThrow(WorldPresets.FLAT))
            val layers = JsonParser.parseString(generation["generatorSettings"].asString)
            val settings = FlatLevelGeneratorSettings.CODEC
                .parse(RegistryOps.create(JsonOps.INSTANCE, registries), layers)
                .getOrThrow()
            state.updateDimensions(PresetEditor.flatWorldConfigurator(settings))
            state.isGenerateStructures = false
        }
        // As in the game test server's session worlds
        state.difficulty = Difficulty.PEACEFUL
    }

    companion object {

        private const val FLAT_LAYERS = """{"biome":"minecraft:plains","layers":[""" +
            """{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},""" +
            """{"block":"minecraft:grass_block","height":1}]}"""

        private const val VOID_LAYERS = """{"biome":"minecraft:the_void","layers":[""" +
            """{"block":"minecraft:air","height":1}]}"""

        /** Superflat plains, the grass at y -61. What every test gets unless it asks for more. */
        val FLAT = TestWorld(
            generation("flat") { addProperty("generatorSettings", FLAT_LAYERS) },
            BlockPos(0, -60, 0),
        )

        /** Nothing but a stone platform of 9 by 9 blocks around the player. */
        val VOID = TestWorld(
            generation("void") { addProperty("generatorSettings", VOID_LAYERS) },
            BlockPos(0, 64, 0),
        ) {
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

        /**
         * A default world from [seed] with the test's own start: the player appears at [origin] once [prepare] has
         * built what it needs there, such as a pad on real terrain.
         */
        fun generated(
            seed: Long,
            origin: BlockPos,
            structures: Boolean = false,
            prepare: GameTestScope.() -> Unit,
        ) = TestWorld(
            generation("normal") {
                addProperty("seed", seed.toString())
                addProperty("structures", structures)
            },
            origin,
            prepare,
        )

        private fun generation(type: String, settings: JsonObject.() -> Unit = {}) =
            JsonObject().apply { addProperty("type", type) }.apply(settings)

    }

}

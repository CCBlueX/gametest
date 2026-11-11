package net.ccbluex.gametest.plugin.session;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.commands.CommandResponseTracker;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

/**
 * Vanilla's {@code forceload add}, except that it leaves loading the chunks to the chunk system: vanilla generates
 * each new one on the server thread, which stalls every other session for as long as that takes. The session's next
 * commands wait for the chunks instead, so they still find them loaded.
 */
final class ForceLoad {

    private static final int MAX_CHUNKS = 256;
    private static final int WORLD_LIMIT = 30_000_000;
    private static final Dynamic2CommandExceptionType TOO_MANY_CHUNKS = new Dynamic2CommandExceptionType(
        (max, amount) -> Component.translatableEscape("commands.forceload.toobig", max, amount));
    private static final SimpleCommandExceptionType ALL_ADDED =
        new SimpleCommandExceptionType(Component.translatable("commands.forceload.added.failure"));
    private static final CommandResponseTracker.MessagesWithArg<ChunkPos, Area> ADDED = CommandResponseTracker.messages(
        ALL_ADDED,
        (chunk, ignored, area) -> Component.translatable("commands.forceload.added.single",
            Component.translationArg(chunk), Component.translationArg(area.dimension().identifier())),
        (count, ignored, area) -> Component.translatable("commands.forceload.added.multiple", count,
            Component.translationArg(area.dimension().identifier()), Component.translationArg(area.min()),
            Component.translationArg(area.max()))
    );

    private ForceLoad() {
    }

    /** Takes over the two {@code forceload add} forms; brigadier keeps vanilla's nodes and their other branches. */
    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("forceload")
            .then(Commands.literal("add")
                .then(Commands.argument("from", ColumnPosArgument.columnPos())
                    .executes(context -> add(context.getSource(), ColumnPosArgument.getColumnPos(context, "from"),
                        ColumnPosArgument.getColumnPos(context, "from")))
                    .then(Commands.argument("to", ColumnPosArgument.columnPos())
                        .executes(context -> add(context.getSource(), ColumnPosArgument.getColumnPos(context, "from"),
                            ColumnPosArgument.getColumnPos(context, "to")))))));
    }

    private static int add(CommandSourceStack source, ColumnPos from, ColumnPos to) throws CommandSyntaxException {
        int minX = Math.min(from.x(), to.x());
        int minZ = Math.min(from.z(), to.z());
        int maxX = Math.max(from.x(), to.x());
        int maxZ = Math.max(from.z(), to.z());
        if (minX < -WORLD_LIMIT || minZ < -WORLD_LIMIT || maxX >= WORLD_LIMIT || maxZ >= WORLD_LIMIT) {
            throw BlockPosArgument.ERROR_OUT_OF_WORLD.create();
        }
        var area = new Area(source.getLevel().dimension(), SectionPos.blockToSectionCoord(minX),
            SectionPos.blockToSectionCoord(minZ), SectionPos.blockToSectionCoord(maxX), SectionPos.blockToSectionCoord(maxZ));
        long count = (area.maxX() - area.minX() + 1L) * (area.maxZ() - area.minZ() + 1L);
        if (count > MAX_CHUNKS) {
            throw TOO_MANY_CHUNKS.create(MAX_CHUNKS, count);
        }

        var level = source.getLevel();
        var tracker = CommandResponseTracker.<ChunkPos>create();
        var loads = new ArrayList<CompletableFuture<?>>();
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                var chunk = new ChunkPos(x, z);
                boolean added = level.getChunkSource().updateChunkForced(chunk, true);
                tracker.track(chunk, added);
                if (added) {
                    loads.add(level.getWorld().getChunkAtAsync(x, z, true, true));
                }
            }
        }
        if (source.source instanceof SessionConsole console) {
            console.await(CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)));
        }
        return tracker.sendFeedback(source, true, ADDED, area);
    }

    private record Area(ResourceKey<Level> dimension, int minX, int minZ, int maxX, int maxZ) {

        ChunkPos min() {
            return new ChunkPos(minX, minZ);
        }

        ChunkPos max() {
            return new ChunkPos(maxX, maxZ);
        }

    }

}

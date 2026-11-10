package net.ccbluex.gametest.plugin.isolation;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The dimension names one test player gets to see. Its session's worlds are the three vanilla dimensions to it,
 * and the lobby, which is the server's real overworld, has a name of its own so that the client builds a new
 * level when it moves between the two.
 */
public record DimensionView(Map<ResourceKey<Level>, ResourceKey<Level>> shown) {

    static final ResourceKey<Level> LOBBY =
        ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("gametest", "lobby"));
    public static final DimensionView WITHOUT_SESSION = new DimensionView(Map.of(Level.OVERWORLD, LOBBY));
    private static final Set<ResourceKey<Level>> LEVELS = Set.of(Level.OVERWORLD, Level.NETHER, Level.END, LOBBY);

    /** A view of the lobby and of a session's worlds, given as real key to the vanilla key they stand for. */
    public static DimensionView of(Map<ResourceKey<Level>, ResourceKey<Level>> sessionWorlds) {
        var shown = new HashMap<>(sessionWorlds);
        shown.put(Level.OVERWORLD, LOBBY);
        return new DimensionView(Map.copyOf(shown));
    }

    ClientboundLoginPacket show(ClientboundLoginPacket packet) {
        return new ClientboundLoginPacket(
            packet.playerId(), packet.hardcore(), LEVELS, packet.maxPlayers(), packet.chunkRadius(),
            packet.simulationDistance(), packet.reducedDebugInfo(), packet.showDeathScreen(),
            packet.doLimitedCrafting(), show(packet.commonPlayerSpawnInfo()), packet.onlineMode(),
            packet.enforcesSecureChat()
        );
    }

    ClientboundRespawnPacket show(ClientboundRespawnPacket packet) {
        return new ClientboundRespawnPacket(show(packet.commonPlayerSpawnInfo()), packet.dataToKeep());
    }

    ClientboundSetDefaultSpawnPositionPacket show(ClientboundSetDefaultSpawnPositionPacket packet) {
        var spawn = packet.respawnData();
        return new ClientboundSetDefaultSpawnPositionPacket(
            new LevelData.RespawnData(show(spawn.globalPos()), spawn.yaw(), spawn.pitch())
        );
    }

    private CommonPlayerSpawnInfo show(CommonPlayerSpawnInfo info) {
        return new CommonPlayerSpawnInfo(
            info.dimensionType(), show(info.dimension()), info.seed(), info.gameType(), info.previousGameType(),
            info.isDebug(), info.isFlat(), info.lastDeathLocation().map(this::show), info.portalCooldown(),
            info.seaLevel()
        );
    }

    private GlobalPos show(GlobalPos position) {
        return GlobalPos.of(show(position.dimension()), position.pos());
    }

    private ResourceKey<Level> show(ResourceKey<Level> dimension) {
        return shown.getOrDefault(dimension, dimension);
    }

}

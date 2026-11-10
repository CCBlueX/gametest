package net.ccbluex.gametest.plugin.session;

import io.papermc.paper.math.Position;
import net.ccbluex.gametest.plugin.Lobby;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Creates and removes the worlds of sessions. Paper keeps every world below the main world's folder: a session's
 * are {@code world/dimensions/gametest/<session>/<overworld|the_nether|the_end>}, and a player's files are in
 * {@code world/players}.
 */
public final class SessionWorlds {

    private static final String NAMESPACE = "gametest";
    private static final int DISTANCE = 4;
    private static final int UNLOAD_ATTEMPTS = 20;
    private static final int OFFLINE_UUID_VERSION = 3;

    private final Plugin plugin;
    private final Lobby lobby;
    private final LevelStorageSource.LevelStorageAccess storage;

    public SessionWorlds(Plugin plugin, Lobby lobby) {
        this.plugin = plugin;
        this.lobby = lobby;
        this.storage = ((CraftServer) plugin.getServer()).getServer().storageSource;
    }

    /** The session's world of that kind. Its Nether and End come into being the first time they are asked for. */
    public World of(Session session, World.Environment environment) {
        var world = session.world(environment);
        if (world == null) {
            world = create(session, environment);
            session.add(environment, world);
        }
        return world;
    }

    /** @throws RuntimeException when the server cannot make a world of what the session asked for */
    private World create(Session session, World.Environment environment) {
        var spec = session.spec();
        var name = switch (environment) {
            case NETHER -> "the_nether";
            case THE_END -> "the_end";
            default -> "overworld";
        };
        var creator = WorldCreator.ofKey(new NamespacedKey(NAMESPACE, session.id() + "/" + name))
            .environment(environment)
            .type(spec.type() == WorldSpec.Type.NORMAL ? WorldType.NORMAL : WorldType.FLAT)
            .generatorSettings(spec.generatorSettings())
            .seed(spec.seed())
            .generateStructures(spec.structures())
            // Without it the server searches a spawn point, which generates chunks on the server thread
            .forcedSpawnPosition(Position.block(0, 0, 0), 0f, 0f);
        var world = creator.createWorld();
        if (world == null) {
            throw new IllegalStateException("the server did not create the world");
        }

        world.setAutoSave(false);
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setViewDistance(DISTANCE);
        world.setSimulationDistance(DISTANCE);
        var level = Session.level(world);
        world.setSpawnLocation(0, level.getChunkSource().getGenerator().getSpawnHeight(level), 0);
        // A wither or dragon of one session must not be heard in every other
        level.getGameRules().set(GameRules.GLOBAL_SOUND_EVENTS, false, level);
        return world;
    }

    /** Unloads the session's worlds without saving and deletes them. Nobody may be left in them but spectators. */
    void discard(Session session) {
        unload(List.copyOf(session.worlds()), session.id(), UNLOAD_ATTEMPTS);
    }

    private void unload(List<World> worlds, String session, int attempts) {
        var loaded = new ArrayList<World>();
        for (var world : worlds) {
            world.getPlayers().forEach(player -> player.teleport(lobby.spawn()));
            if (!Bukkit.unloadWorld(world, false)) {
                loaded.add(world);
            }
        }
        if (!loaded.isEmpty() && attempts > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> unload(loaded, session, attempts - 1), 1);
            return;
        }
        if (!loaded.isEmpty()) {
            plugin.getSLF4JLogger().warn("Could not unload {}, what is left of it goes with the next start", loaded);
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> delete(folder(session)));
    }

    /** Test players never come back under the same name once their session is over, so nothing of them is kept. */
    void deletePlayerFiles(UUID player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (var directory : playerDirectories()) {
                try (var files = Files.newDirectoryStream(directory, player + "*")) {
                    files.forEach(this::delete);
                } catch (IOException exception) {
                    plugin.getSLF4JLogger().warn("Could not list {}", directory, exception);
                }
            }
        });
    }

    /** What a stopped or crashed server left behind: every session world, and the files of every offline player. */
    public void deleteLeftovers() {
        delete(folder(""));
        for (var directory : playerDirectories()) {
            try (var files = Files.newDirectoryStream(directory, SessionWorlds::belongsToOfflinePlayer)) {
                files.forEach(this::delete);
            } catch (IOException exception) {
                plugin.getSLF4JLogger().warn("Could not list {}", directory, exception);
            }
        }
    }

    private List<Path> playerDirectories() {
        return Stream.of(
            LevelResource.PLAYER_DATA_DIR, LevelResource.PLAYER_STATS_DIR, LevelResource.PLAYER_ADVANCEMENTS_DIR
        ).map(storage::getLevelPath).filter(Files::isDirectory).toList();
    }

    private static boolean belongsToOfflinePlayer(Path file) {
        var name = file.getFileName().toString();
        try {
            return name.length() > 36 && UUID.fromString(name.substring(0, 36)).version() == OFFLINE_UUID_VERSION;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /** The folder of one session's worlds, or of all of them for an empty name. */
    private Path folder(String session) {
        var path = session.isEmpty() ? "any" : session + "/any";
        var inside = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(NAMESPACE, path));
        return storage.getDimensionPath(inside).getParent();
    }

    private void delete(Path path) {
        if (Files.notExists(path)) {
            return;
        }
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                    Files.delete(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | UncheckedIOException exception) {
            plugin.getSLF4JLogger().warn("Could not delete {}", path, exception);
        }
    }

}

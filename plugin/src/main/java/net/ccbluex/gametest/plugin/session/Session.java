package net.ccbluex.gametest.plugin.session;

import com.google.gson.JsonObject;
import net.ccbluex.gametest.plugin.isolation.DimensionView;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * One test player's stay: up to three worlds nobody else plays in, from {@code open} until the player is gone
 * for good. Changed on the server thread only; {@link #view()} and {@link #owns(ServerLevel)} are read anywhere.
 */
public final class Session {

    private final String id;
    private final UUID playerId;
    private final String playerName;
    private final WorldSpec spec;
    private final Info info;
    private final long opened = System.currentTimeMillis();
    private final Map<World.Environment, World> worlds = new EnumMap<>(World.Environment.class);
    private volatile DimensionView view = DimensionView.WITHOUT_SESSION;
    private volatile Set<ServerLevel> levels = Set.of();
    private volatile Map<String, String> shownNames = Map.of();

    /** Set by {@code close}: the session ends with the player's next quit. */
    boolean closing;
    /** Ends the session unless the player comes back in time. */
    @Nullable BukkitTask expiry;
    @Nullable BukkitTask lifetime;
    @Nullable BukkitTask closeTimeout;
    /** When the player last started to join, which happens off the server thread. */
    volatile long returning;
    /** Set once the player has arrived in the session's overworld after {@code open}. */
    boolean entered;
    /** What earlier commands started and later ones wait for, such as chunks loading. */
    CompletableFuture<?> pending = CompletableFuture.completedFuture(null);
    /** Commands that came in while {@link #pending} was not done, in their order. */
    final Queue<Runnable> waiting = new ArrayDeque<>();

    Session(String id, Player player, WorldSpec spec, Info info) {
        this.id = id;
        this.playerId = player.getUniqueId();
        this.playerName = player.getName();
        this.spec = spec;
        this.info = info;
    }

    public String id() {
        return id;
    }

    public UUID playerId() {
        return playerId;
    }

    public String playerName() {
        return playerName;
    }

    WorldSpec spec() {
        return spec;
    }

    public Info info() {
        return info;
    }

    public Duration age() {
        return Duration.ofMillis(System.currentTimeMillis() - opened);
    }

    DimensionView view() {
        return view;
    }

    public Collection<World> worlds() {
        return worlds.values();
    }

    @Nullable World world(World.Environment environment) {
        return worlds.get(environment);
    }

    public World overworld() {
        return worlds.get(World.Environment.NORMAL);
    }

    public boolean owns(World world) {
        return worlds.containsValue(world);
    }

    public boolean owns(ServerLevel level) {
        return levels.contains(level);
    }

    void add(World.Environment environment, World world) {
        worlds.put(environment, world);
        levels = worlds.values().stream().map(Session::level).collect(Collectors.toUnmodifiableSet());

        var shown = new HashMap<ResourceKey<Level>, ResourceKey<Level>>();
        worlds.forEach((kind, owned) -> shown.put(level(owned).dimension(), vanillaKey(kind)));
        view = DimensionView.of(shown);
        shownNames = shown.entrySet().stream().collect(Collectors.toUnmodifiableMap(
            entry -> entry.getKey().identifier().toString(), entry -> entry.getValue().identifier().toString()));
    }

    /** The server's feedback and log name the session's worlds by their real keys, the harness by the vanilla ones. */
    public String withShownNames(String text) {
        for (var name : shownNames.entrySet()) {
            text = text.replace(name.getKey(), name.getValue());
        }
        return text;
    }

    public static ServerLevel level(World world) {
        return ((CraftWorld) world).getHandle();
    }

    static ResourceKey<Level> vanillaKey(World.Environment environment) {
        return switch (environment) {
            case NETHER -> Level.NETHER;
            case THE_END -> Level.END;
            default -> Level.OVERWORLD;
        };
    }

    /** What the harness said about the test in {@code open}, for whoever watches. */
    public record Info(String repository, String run, String test) {

        private static final int MAX_LENGTH = 120;

        static Info parse(@Nullable JsonObject info) {
            return info == null
                ? new Info("", "", "")
                : new Info(text(info, "repository"), text(info, "run"), text(info, "test"));
        }

        private static String text(JsonObject info, String field) {
            var text = WorldSpec.string(info, field, "");
            return text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH);
        }

    }

}

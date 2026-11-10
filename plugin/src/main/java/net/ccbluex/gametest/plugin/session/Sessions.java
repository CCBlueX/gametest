package net.ccbluex.gametest.plugin.session;

import com.google.gson.JsonObject;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import net.ccbluex.gametest.plugin.Lobby;
import net.ccbluex.gametest.plugin.TestPlayers;
import net.ccbluex.gametest.plugin.control.Control;
import net.ccbluex.gametest.plugin.isolation.DimensionView;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The sessions that are open, and what the harness asks for on the control channel. Everything but the lookups
 * by player id runs on the server thread.
 */
public final class Sessions implements Listener {

    private static final int TICKS_PER_SECOND = 20;
    /** How long a session waits for a player who left without {@code close}. */
    private static final int RECONNECT_SECONDS = 60;
    private static final int LIFETIME_SECONDS = 30 * 60;
    /** How long a player may stay after {@code close}. */
    private static final int CLOSE_SECONDS = 10;
    private static final int BUSY_RETRY_SECONDS = 5;
    /** A join that has picked the session's world as its spawn must not find the world gone when it gets there. */
    private static final int JOIN_SECONDS = 15;

    private final Plugin plugin;
    private final Lobby lobby;
    private final SessionWorlds worlds;
    private final SessionCommands commands;
    private final int limit;
    private final Map<UUID, Session> byPlayer = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public Sessions(Plugin plugin, Lobby lobby, SessionWorlds worlds, SessionCommands commands, int limit) {
        this.plugin = plugin;
        this.lobby = lobby;
        this.worlds = worlds;
        this.commands = commands;
        this.limit = limit;
    }

    public Collection<Session> all() {
        return byPlayer.values();
    }

    public @Nullable Session of(Player player) {
        return byPlayer.get(player.getUniqueId());
    }

    public @Nullable Session owning(World world) {
        return byPlayer.values().stream().filter(session -> session.owns(world)).findFirst().orElse(null);
    }

    /** How a player sees the dimensions, or null for a spectator, who sees them as they are. */
    public @Nullable DimensionView view(UUID player, String name) {
        if (!TestPlayers.matches(name)) {
            return null;
        }
        var session = byPlayer.get(player);
        return session == null ? DimensionView.WITHOUT_SESSION : session.view();
    }

    public void open(Player player, JsonObject message) {
        var session = of(player);
        if (session != null) {
            if (session.closing) {
                Control.error(player, "This session was closed");
            } else {
                Control.ready(player, session, true);
            }
            return;
        }
        if (byPlayer.size() >= limit) {
            Control.busy(player, BUSY_RETRY_SECONDS);
            return;
        }

        var spec = WorldSpec.parse(message.getAsJsonObject("world"));
        session = new Session(newId(), player, spec, Session.Info.parse(message.getAsJsonObject("info")));
        try {
            worlds.of(session, World.Environment.NORMAL);
        } catch (RuntimeException exception) {
            plugin.getSLF4JLogger().warn("Could not create the world of a session", exception);
            worlds.discard(session);
            Control.error(player, "The server could not create the world: " + exception.getMessage());
            return;
        }

        var opened = session;
        byPlayer.put(player.getUniqueId(), opened);
        opened.lifetime = later(LIFETIME_SECONDS, () -> end(opened, "it reached its time limit"));
        player.setGameMode(GameMode.SURVIVAL);
        plugin.getSLF4JLogger().info("Session {} opened: {}", opened.id(), opened.info());
        Control.ready(player, opened, false);
    }

    public void command(Player player, JsonObject message) {
        var session = of(player);
        if (session == null) {
            Control.error(player, "Send open before the first command");
            return;
        }
        if (!message.has("id") || !message.has("line")) {
            Control.error(player, "A command needs an id and a line");
            return;
        }
        commands.run(session, player, message.get("id").getAsInt(), message.get("line").getAsString());
    }

    public void close(Player player) {
        var session = of(player);
        if (session == null) {
            Control.error(player, "There is no session to close");
            return;
        }
        session.closing = true;
        cancel(session.closeTimeout);
        var farewell = Component.text("The game test session was closed.");
        session.closeTimeout = later(CLOSE_SECONDS, () -> player.kick(farewell));
        Control.closed(player);
    }

    public void drop(Player player) {
        player.kick(Component.translatable("multiplayer.disconnect.server_shutdown"));
    }

    /** Where a joining player appears: in the session it left, or else in the lobby, whatever its saved data says. */
    @EventHandler
    public void onSpawnLocation(AsyncPlayerSpawnLocationEvent event) {
        var session = byPlayer.get(event.getConnection().getProfile().getId());
        var world = event.getSpawnLocation().getWorld();
        if (session == null || event.isNewPlayer() || !session.owns(Session.level(world))) {
            event.setSpawnLocation(lobby.spawn());
        } else {
            session.returning = System.currentTimeMillis();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        var session = of(event.getPlayer());
        if (session != null && session.expiry != null) {
            session.expiry.cancel();
            session.expiry = null;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        var session = of(player);
        if (session == null) {
            if (TestPlayers.matches(player)) {
                later(0, () -> worlds.deletePlayerFiles(player.getUniqueId()));
            }
        } else if (session.closing) {
            // The player is still in its world while this event runs
            later(0, () -> end(session, "its test is over"));
        } else {
            session.expiry = later(RECONNECT_SECONDS, () -> expire(session));
        }
    }

    private void expire(Session session) {
        var joining = System.currentTimeMillis() - session.returning < JOIN_SECONDS * 1000L;
        if (joining) {
            session.expiry = later(JOIN_SECONDS, () -> expire(session));
        } else {
            end(session, "its player did not come back");
        }
    }

    private void end(Session session, String reason) {
        if (!byPlayer.remove(session.playerId(), session)) {
            return;
        }
        cancel(session.expiry);
        cancel(session.lifetime);
        cancel(session.closeTimeout);
        var player = Bukkit.getPlayer(session.playerId());
        if (player != null) {
            player.kick(Component.text("The game test session ended: " + reason + "."));
        }
        plugin.getSLF4JLogger().info("Session {} ended: {}", session.id(), reason);
        worlds.discard(session);
        worlds.deletePlayerFiles(session.playerId());
        if (byPlayer.isEmpty()) {
            SharedState.clear();
        }
    }

    private BukkitTask later(int seconds, Runnable task) {
        return Bukkit.getScheduler().runTaskLater(plugin, task, (long) seconds * TICKS_PER_SECOND);
    }

    private static void cancel(@Nullable BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    private String newId() {
        var bytes = new byte[4];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

}

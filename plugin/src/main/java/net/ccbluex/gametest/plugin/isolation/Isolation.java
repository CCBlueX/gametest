package net.ccbluex.gametest.plugin.isolation;

import io.papermc.paper.event.entity.EntityPortalReadyEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PlayerSignCommandPreprocessEvent;
import net.ccbluex.gametest.plugin.Lobby;
import net.ccbluex.gametest.plugin.TestPlayers;
import net.ccbluex.gametest.plugin.WatchCommand;
import net.ccbluex.gametest.plugin.session.Session;
import net.ccbluex.gametest.plugin.session.SessionWorlds;
import net.ccbluex.gametest.plugin.session.Sessions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.commands.arguments.selector.EntitySelector;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.PortalType;
import org.bukkit.World;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.CommandMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.Plugin;

/**
 * Keeps players apart. A test player sees nobody else and nothing anybody else does, and nobody but a session's
 * own player gets into its worlds, except spectators, who watch and touch nothing.
 */
public final class Isolation implements Listener {

    private final Plugin plugin;
    private final Lobby lobby;
    private final Sessions sessions;
    private final SessionWorlds worlds;

    public Isolation(Plugin plugin, Lobby lobby, Sessions sessions, SessionWorlds worlds) {
        this.plugin = plugin;
        this.lobby = lobby;
        this.sessions = sessions;
        this.worlds = worlds;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var test = TestPlayers.matches(player);
        event.joinMessage(null);
        // Before the server tells everybody about everybody: hidden players are left out of the tab list as well
        for (var other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) {
                continue;
            }
            if (TestPlayers.matches(other)) {
                other.hidePlayer(plugin, player);
            }
            if (test) {
                player.hidePlayer(plugin, other);
            }
        }

        if (!test) {
            player.setGameMode(GameMode.SPECTATOR);
        } else if (sessions.of(player) == null) {
            player.setGameMode(GameMode.ADVENTURE);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        var sender = event.getPlayer();
        event.viewers().removeIf(viewer ->
            viewer instanceof Player player && !player.equals(sender) && TestPlayers.matches(player));
    }

    /** The death screen keeps its message, and so does the server log, but no other test player reads it in chat. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        var message = event.deathMessage();
        if (message != null) {
            event.deathScreenMessageOverride(message);
            event.deathMessage(null);
            if (event.getShowDeathMessages()) {
                announce(event.getPlayer(), message);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        var message = event.message();
        if (message != null) {
            event.message(null);
            announce(event.getPlayer(), message);
        }
    }

    private void announce(Player about, Component message) {
        Bukkit.getConsoleSender().sendMessage(message);
        for (var player : Bukkit.getOnlinePlayers()) {
            if (player.equals(about) || !TestPlayers.matches(player)) {
                player.sendMessage(message);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        var target = event.getTo().getWorld();
        if (!target.equals(event.getFrom().getWorld()) && !mayEnter(event.getPlayer(), target)) {
            event.setCancelled(true);
        }
    }

    private boolean mayEnter(Player player, World world) {
        if (!TestPlayers.matches(player) || lobby.contains(world)) {
            return true;
        }
        var session = sessions.of(player);
        return session != null && session.owns(world);
    }

    /** Vanilla sends a player without a bed to the server's spawn, which here is the lobby. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onRespawn(PlayerRespawnEvent event) {
        var session = sessions.of(event.getPlayer());
        if (session != null && !session.owns(event.getRespawnLocation().getWorld())) {
            event.setRespawnLocation(spawn(session));
        }
    }

    private static Location spawn(Session session) {
        var spawn = session.overworld().getSpawnLocation();
        spawn.setY(session.overworld().getHighestBlockYAt(spawn) + 1);
        return spawn.add(0.5, 0, 0.5);
    }

    /** The server has no Nether of its own. A Nether portal in a session leads to the session's Nether and back. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPortal(EntityPortalReadyEvent event) {
        var world = event.getEntity().getWorld();
        var session = sessions.owning(world);
        if (session == null || event.getPortalType() != PortalType.NETHER) {
            event.setCancelled(true);
            return;
        }
        var inNether = world.getEnvironment() == World.Environment.NETHER;
        event.setTargetWorld(worlds.of(session, inNether ? World.Environment.NORMAL : World.Environment.NETHER));
    }

    /** The exit portal of an End leads to the server's spawn. From a session's End it leads to the session's. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        var session = sessions.owning(event.getFrom().getWorld());
        if (session == null) {
            event.setCancelled(true);
        } else if (event.getTo() == null || !session.owns(event.getTo().getWorld())) {
            event.setTo(spawn(session));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        var session = sessions.owning(event.getFrom().getWorld());
        if (session == null) {
            event.setCancelled(true);
        } else if (!session.owns(event.getTo().getWorld())) {
            event.setTo(spawn(session));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (lobby.contains(event.getEntity().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHunger(FoodLevelChangeEvent event) {
        if (lobby.contains(event.getEntity().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        if (lobby.contains(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    /** Placing blocks, emptying buckets, lighting fires: nothing a player holds does anything in the lobby. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (lobby.contains(event.getPlayer().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        var player = event.getPlayer();
        if (!TestPlayers.matches(player) && !player.isOp() && event.getNewGameMode() != GameMode.SPECTATOR) {
            event.setCancelled(true);
        }
    }

    /** Command blocks and their minecarts run commands that no session confines. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onServerCommand(ServerCommandEvent event) {
        if (event.getSender() instanceof BlockCommandSender || event.getSender() instanceof CommandMinecart) {
            event.setCancelled(true);
        }
    }

    /**
     * What players type themselves. A test player's command must not reach anybody else, a sign's click command
     * would run above the player's own permissions, and spectators only watch.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        var player = event.getPlayer();
        var command = event.getMessage().substring(1);
        var root = command.split(" ", 2)[0].toLowerCase();
        if (event instanceof PlayerSignCommandPreprocessEvent) {
            event.setCancelled(true);
        } else if (!TestPlayers.matches(player)) {
            if (!player.isOp() && !root.equals(WatchCommand.NAME)) {
                event.setCancelled(true);
                var hint = "Spectators only have /" + WatchCommand.NAME + ".";
                player.sendMessage(Component.text(hint, NamedTextColor.RED));
            }
        } else if (Confinement.isRefusedRoot(root)) {
            event.setCancelled(true);
            player.sendMessage(Component.translatable("command.unknown.command", NamedTextColor.RED));
        } else if (namesSomebodyElse(player, command)) {
            event.setCancelled(true);
            player.sendMessage(Component.translatable("argument.entity.notfound.player", NamedTextColor.RED));
        }
    }

    private boolean namesSomebodyElse(Player player, String command) {
        var server = ((CraftServer) plugin.getServer()).getServer();
        var source = ((CraftPlayer) player).getHandle().createCommandSourceStack();
        var parse = server.getCommands().getDispatcher().parse(command, source);
        for (var context = parse.getContext(); context != null; context = context.getChild()) {
            for (var argument : context.getArguments().values()) {
                if (argument.getResult() instanceof EntitySelector selector && namesSomebodyElse(player, selector)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean namesSomebodyElse(Player player, EntitySelector selector) {
        var name = Internals.playerName(selector);
        var id = Internals.entityId(selector);
        var named = name != null ? Bukkit.getPlayerExact(name) : id != null ? Bukkit.getPlayer(id) : null;
        return named != null && !named.equals(player);
    }

}

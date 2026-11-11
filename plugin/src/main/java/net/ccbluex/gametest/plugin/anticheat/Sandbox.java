package net.ccbluex.gametest.plugin.anticheat;

import dev.diona.pluginhooker.api.PluginHookerAPI;
import dev.diona.pluginhooker.api.event.PlayerHookRegisterEvent;
import dev.diona.pluginhooker.api.plugin.HookMode;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Puts every installed anticheat into a PluginHooker sandbox: its listeners hear only the test players it watches,
 * and players of other anticheats, Vanilla bots and spectators are hidden from it.
 */
public final class Sandbox implements Listener {

    private final Plugin owner;

    private Sandbox(Plugin owner) {
        this.owner = owner;
    }

    public static Sandbox start(Plugin owner) {
        var registry = PluginHookerAPI.getPluginRegistry();
        for (var anticheat : Anticheat.values()) {
            var plugin = anticheat.plugin();
            if (plugin != null) {
                // An anticheat that sets players up while they log in has to see every login; the sandbox decides
                // who it watches once the player is known
                registry.registerPlugin(plugin, anticheat.setsUpOnLogin() ? HookMode.BLACKLIST : HookMode.WHITELIST);
            }
        }
        return new Sandbox(owner);
    }

    // Before the join packet, so the anticheat sets the player up as it would any other
    @EventHandler
    public void onHook(PlayerHookRegisterEvent event) {
        var watching = Anticheat.of(event.getPlayer());
        for (var anticheat : Anticheat.values()) {
            var plugin = anticheat.plugin();
            if (plugin == null) {
                continue;
            }
            if (anticheat == watching) {
                event.getHookedPlayer().enablePlugin(plugin);
            } else {
                event.getHookedPlayer().disablePlugin(plugin);
            }
        }
    }

    /**
     * PluginHooker forgets a player only when it sees the channel close, and on Paper 26.3 it never finds the channel,
     * so it would keep every player and the world each one left from. A tick after the quit no event concerns them.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        Bukkit.getScheduler().runTask(owner, () -> {
            var registry = PluginHookerAPI.getPlayerRegistry();
            var hooked = registry.getPlayer(player.getUniqueId());
            // Unless the player has come back on a new connection already
            if (hooked != null && hooked.getBukkitPlayer() == player) {
                registry.unregisterPlayer(player);
            }
        });
    }

}

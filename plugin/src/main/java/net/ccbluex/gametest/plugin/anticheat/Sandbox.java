package net.ccbluex.gametest.plugin.anticheat;

import dev.diona.pluginhooker.api.PluginHookerAPI;
import dev.diona.pluginhooker.api.event.PlayerHookRegisterEvent;
import dev.diona.pluginhooker.api.plugin.HookMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Puts every installed anticheat into a PluginHooker sandbox: its listeners hear only the test players it watches,
 * and players of other anticheats, Vanilla bots and spectators are hidden from it.
 */
public final class Sandbox implements Listener {

    private Sandbox() {
    }

    public static Sandbox start() {
        var registry = PluginHookerAPI.getPluginRegistry();
        for (var anticheat : Anticheat.values()) {
            var plugin = anticheat.plugin();
            if (plugin != null) {
                // An anticheat that sets players up while they log in has to see every login; the sandbox decides
                // who it watches once the player is known
                registry.registerPlugin(plugin, anticheat.setsUpOnLogin() ? HookMode.BLACKLIST : HookMode.WHITELIST);
            }
        }
        return new Sandbox();
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

}

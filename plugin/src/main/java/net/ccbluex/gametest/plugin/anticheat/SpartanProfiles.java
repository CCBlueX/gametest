package net.ccbluex.gametest.plugin.anticheat;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Spartan keeps a profile of everyone who ever joined, and the profile keeps the player and the world it left from.
 * After the quit the profile lets go of the player, as Spartan's own profiles of offline players do. Spartan has no
 * API for its profiles, so they are found by name.
 */
public final class SpartanProfiles implements Listener {

    private final Plugin owner;
    private final Map<String, ?> profiles;
    private final Field player;

    private SpartanProfiles(Plugin owner, Map<String, ?> profiles, Field player) {
        this.owner = owner;
        this.profiles = profiles;
        this.player = player;
    }

    @SuppressWarnings("unchecked")
    public static void start(Plugin owner) throws ReflectiveOperationException {
        var spartan = Anticheat.SPARTAN.plugin();
        if (spartan == null) {
            return;
        }
        var events = new PluginEvents(owner, spartan);
        var profiles = accessible(events.type("ai.idealistic.spartan.functionality.tracking.ResearchEngine"),
            "playerProfiles");
        var player = accessible(events.type("ai.idealistic.spartan.abstraction.profiling.PlayerProfile"),
            "offlinePlayer");
        var listener = new SpartanProfiles(owner, (Map<String, ?>) profiles.get(null), player);
        Bukkit.getPluginManager().registerEvents(listener, owner);
    }

    // A tick later, when Spartan is done with the player
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        var gone = event.getPlayer();
        Bukkit.getScheduler().runTask(owner, () -> {
            var profile = profiles.get(gone.getName());
            try {
                // Unless the player has come back on a new connection already
                if (profile != null && player.get(profile) == gone) {
                    player.set(profile, null);
                }
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private static Field accessible(Class<?> owner, String name) throws NoSuchFieldException {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

}

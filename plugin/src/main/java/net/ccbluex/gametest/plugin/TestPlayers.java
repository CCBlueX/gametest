package net.ccbluex.gametest.plugin;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * The proxy in front lets a name of this pattern in only for a bot that proved where it comes from, and never
 * for a real account, so the name alone tells a test player from a spectator. The online ones are kept for
 * threads other than the server's.
 */
public final class TestPlayers implements Listener {

    private static final Pattern NAME = Pattern.compile("^GT_[A-Za-z0-9]{1,13}$");

    private final Map<UUID, Player> online = new ConcurrentHashMap<>();

    public static boolean matches(String name) {
        return NAME.matcher(name).matches();
    }

    public static boolean matches(Player player) {
        return matches(player.getName());
    }

    public Collection<Player> online() {
        return online.values();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        if (matches(event.getPlayer())) {
            online.put(event.getPlayer().getUniqueId(), event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        online.remove(event.getPlayer().getUniqueId());
    }

}

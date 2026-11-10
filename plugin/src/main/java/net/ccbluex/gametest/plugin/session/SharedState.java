package net.ccbluex.gametest.plugin.session;

import org.bukkit.Bukkit;
import org.bukkit.boss.KeyedBossBar;

import java.util.ArrayList;

/**
 * Scoreboard objectives, teams and boss bars belong to the whole server, so tests name theirs uniquely and
 * leave them behind. They are thrown away whenever no session is open, or they would pile up forever.
 */
public final class SharedState {

    private SharedState() {
    }

    public static void clear() {
        var scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        scoreboard.getObjectives().forEach(objective -> objective.unregister());
        scoreboard.getTeams().forEach(team -> team.unregister());

        var bars = new ArrayList<KeyedBossBar>();
        Bukkit.getBossBars().forEachRemaining(bars::add);
        bars.forEach(bar -> {
            bar.removeAll();
            Bukkit.removeBossBar(bar.getKey());
        });
    }

}

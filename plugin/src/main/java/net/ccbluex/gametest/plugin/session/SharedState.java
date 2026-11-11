package net.ccbluex.gametest.plugin.session;

import net.minecraft.nbt.CompoundTag;
import org.bukkit.Bukkit;
import org.bukkit.boss.KeyedBossBar;
import org.bukkit.craftbukkit.CraftServer;

import java.util.ArrayList;

/**
 * Scoreboard objectives, teams, boss bars and command storage belong to the whole server, so tests name theirs
 * uniquely and leave them behind. They are thrown away whenever no session is open, or they would pile up forever.
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

        var storage = ((CraftServer) Bukkit.getServer()).getServer().getCommandStorage();
        storage.keys().toList().forEach(id -> storage.set(id, new CompoundTag()));
    }

}

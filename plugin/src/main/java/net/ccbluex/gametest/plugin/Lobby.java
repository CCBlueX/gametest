package net.ccbluex.gametest.plugin;

import net.minecraft.world.level.gamerules.GameRules;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;

/**
 * The server's main world, where players wait: a room of barriers in the void. Test players stand in it until
 * their harness has opened a session and teleports them out.
 */
public record Lobby(World world, Location spawn) {

    private static final int FLOOR = 64;
    private static final int RADIUS = 4;
    private static final int HEIGHT = 5;

    static Lobby prepare(Server server) {
        var world = server.getWorlds().getFirst();
        for (var x = -RADIUS; x <= RADIUS; x++) {
            for (var z = -RADIUS; z <= RADIUS; z++) {
                for (var y = FLOOR; y <= FLOOR + HEIGHT; y++) {
                    var shell = Math.abs(x) == RADIUS || Math.abs(z) == RADIUS || y == FLOOR || y == FLOOR + HEIGHT;
                    world.getBlockAt(x, y, z).setType(shell ? Material.BARRIER : Material.AIR, false);
                }
            }
        }

        world.setSpawnLocation(0, FLOOR + 1, 0);
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setTime(6000);
        world.setStorm(false);
        var level = ((CraftWorld) world).getHandle();
        var rules = level.getGameRules();
        rules.set(GameRules.ADVANCE_TIME, false, level);
        rules.set(GameRules.ADVANCE_WEATHER, false, level);
        rules.set(GameRules.SPAWN_MOBS, false, level);
        return new Lobby(world, new Location(world, 0.5, FLOOR + 1, 0.5));
    }

    @Override
    public Location spawn() {
        return spawn.clone();
    }

    public boolean contains(World world) {
        return this.world.equals(world);
    }

}

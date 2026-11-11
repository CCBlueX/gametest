package net.ccbluex.gametest.plugin.isolation;

import net.ccbluex.gametest.plugin.session.Session;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * What one session may cost the server it shares with every other: how many commands it sends, how long they run,
 * how many entities and force-loaded chunks its worlds hold. A test stays far below all of it.
 */
public final class Budget implements Listener {

    public static final int MAX_LINE = 16_384;
    private static final double BURST = 400;
    private static final double COMMANDS_PER_SECOND = 100;
    private static final int MAX_ENTITIES = 1024;
    private static final int MAX_FORCED_CHUNKS = 256;
    private static final long SLOW_NANOS = 1_000_000_000L;

    private final Logger logger;
    private final Function<World, @Nullable Session> owning;
    private final Map<Session, Bucket> buckets = new WeakHashMap<>();

    /** @param owning the session a world belongs to, or null for any other world */
    public Budget(Logger logger, Function<World, @Nullable Session> owning) {
        this.logger = logger;
        this.owning = owning;
    }

    /** Why the session may not run another command now, or null if it may. */
    public @Nullable String admit(Session session, String line) {
        if (line.length() > MAX_LINE) {
            return "A command line has at most " + MAX_LINE + " characters";
        }
        var bucket = buckets.computeIfAbsent(session, _ -> new Bucket());
        return bucket.take() ? null : "More than " + (int) COMMANDS_PER_SECOND + " commands a second";
    }

    /**
     * Notes a command that held up the server, so that whoever sent it can be found, and trims forced chunks. Only
     * at info: a slow command is the server's load, not something the test did wrong.
     */
    public void ran(Session session, String line, long nanos) {
        if (nanos > SLOW_NANOS) {
            logger.info("Session {} of {} ran a command for {} ms: {}",
                session.id(), session.playerName(), nanos / 1_000_000, line);
        }
        for (var world : session.worlds()) {
            unforceBeyondLimit(world);
        }
    }

    private static void unforceBeyondLimit(World world) {
        var forced = world.getForceLoadedChunks();
        if (forced.size() > MAX_FORCED_CHUNKS) {
            forced.stream().skip(MAX_FORCED_CHUNKS).forEach(chunk -> chunk.setForceLoaded(false));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent event) {
        var session = owning.apply(event.getLocation().getWorld());
        if (session == null) {
            return;
        }
        var entities = session.worlds().stream().mapToInt(World::getEntityCount).sum();
        if (entities >= MAX_ENTITIES) {
            event.setCancelled(true);
        }
    }

    /** Commands a session may send: a burst at once, then a steady rate. */
    private static final class Bucket {

        private double tokens = BURST;
        private long refilledAt = System.nanoTime();

        boolean take() {
            var now = System.nanoTime();
            tokens = Math.min(BURST, tokens + (now - refilledAt) / 1e9 * COMMANDS_PER_SECOND);
            refilledAt = now;
            if (tokens < 1) {
                return false;
            }
            tokens--;
            return true;
        }

    }

}

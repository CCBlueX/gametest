package net.ccbluex.gametest.plugin.anticheat;

import io.papermc.paper.event.player.PlayerFailMoveEvent;
import net.ccbluex.gametest.plugin.TestPlayers;
import net.ccbluex.gametest.plugin.control.Control;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tells a test player's harness whenever the server objects to it: the player's anticheat, which prints next to
 * nothing by default, and Paper, which corrects a movement without a word.
 */
public final class Probe implements Listener {

    private final TestPlayers players;

    private Probe(TestPlayers players) {
        this.players = players;
    }

    public static Probe start(Plugin owner, TestPlayers players) throws ReflectiveOperationException {
        var probe = new Probe(players);
        for (var anticheat : Anticheat.values()) {
            var plugin = anticheat.plugin();
            if (plugin == null) {
                continue;
            }
            var events = new PluginEvents(owner, plugin);
            switch (anticheat) {
                case VANILLA -> { }
                case GRIM -> GrimProbe.start(owner, probe);
                case INTAVE -> IntaveProbe.start(events, probe);
                case VULCAN -> VulcanProbe.start(events, probe);
                case MATRIX -> MatrixProbe.start(events, probe);
                case SPARTAN -> SpartanProbe.start(events, probe);
                case MX -> MxProbe.start(events, probe);
                case ANTICHEATADDITION -> AdditionProbe.start(events, probe);
                case FAIRPLAY -> FairPlayProbe.start(events, probe);
            }
        }
        return probe;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFailMove(PlayerFailMoveEvent event) {
        if (!event.isAllowed()) {
            report(event.getPlayer(), "FAILMOVE", event.getFailReason());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        var reason = PlainTextComponentSerializer.plainText().serialize(event.reason());
        report(event.getPlayer(), "KICK", event.getCause(), reason);
    }

    /** A flag of {@code anticheat}: the check as one word, its violations and whatever else the anticheat adds. */
    void flag(Anticheat anticheat, UUID player, Object check, Object violations, Object... details) {
        flagAs(anticheat, player, "FLAG", check, violations, details);
    }

    /**
     * A flag reported as {@code report}, such as NOTE for a check the anticheat calls experimental. Not an overload of
     * {@link #flag}, which would take a check given as a String for the report.
     */
    void flagAs(Anticheat anticheat, UUID player, String report, Object check, Object violations, Object... details) {
        var fields = new Object[details.length + 2];
        fields[0] = String.valueOf(check).replaceAll("\\s+", "");
        fields[1] = violations;
        System.arraycopy(details, 0, fields, 2, details.length);
        report(anticheat, player, report, fields);
    }

    /** {@code anticheat} put the player back to where it stands now. */
    void setback(Anticheat anticheat, Player player) {
        var location = player.getLocation();
        report(anticheat, player.getUniqueId(), "SETBACK", location.getX(), location.getY(), location.getZ());
    }

    /** What {@code anticheat} reports about a player counts only for the test players it watches. */
    void report(Anticheat anticheat, UUID player, String report, Object... fields) {
        players.online().stream()
            .filter(online -> online.getUniqueId().equals(player) && Anticheat.of(online) == anticheat)
            .forEach(online -> report(online, report, fields));
    }

    private void report(Player player, String report, Object... fields) {
        if (TestPlayers.matches(player)) {
            Control.report(player, report, Arrays.stream(fields).map(String::valueOf).collect(Collectors.joining(" ")));
        }
    }

}

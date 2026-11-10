package net.ccbluex.gametest.plugin.anticheat;

import org.bukkit.entity.Player;

import static net.ccbluex.gametest.plugin.anticheat.PluginEvents.getter;

/** Intave's violations. */
final class IntaveProbe {

    private IntaveProbe() {
    }

    static void start(PluginEvents events, Probe probe) throws ReflectiveOperationException {
        var violation = events.event("de.jpx3.intave.access.check.event.IntaveViolationEvent");
        var player = getter(violation, "player");
        var check = getter(violation, "checkName");
        var message = getter(violation, "message");
        var details = getter(violation, "details");
        var violations = getter(violation, "violationLevelAfterViolation");
        // Intave raises it on network threads as well
        events.listen(violation, event -> {
            var level = (double) violations.invoke(event);
            // Intave notes some things without counting them, such as a client that stalls while loading
            if (level <= 0) {
                return;
            }
            probe.flag(Anticheat.INTAVE, ((Player) player.invoke(event)).getUniqueId(), check.invoke(event), level,
                message.invoke(event), details.invoke(event));
        });
    }

}

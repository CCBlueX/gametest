package net.ccbluex.gametest.plugin.anticheat;

import org.bukkit.entity.Player;

import static net.ccbluex.gametest.plugin.anticheat.PluginEvents.getter;

/** Matrix's violations. The commands it punishes with never run. */
final class MatrixProbe {

    private static final String EVENTS = "me.rerere.matrix.api.events.";

    private MatrixProbe() {
    }

    static void start(PluginEvents events, Probe probe) throws ReflectiveOperationException {
        var violation = events.event(EVENTS + "PlayerViolationEvent");
        var player = getter(violation, "getPlayer");
        var hackType = getter(violation, "getHackType");
        var component = getter(violation, "getComponent");
        var violations = getter(violation, "getViolations");
        var message = getter(violation, "getMessage");
        events.listen(violation, event -> probe.flag(Anticheat.MATRIX, ((Player) player.invoke(event)).getUniqueId(),
            hackType.invoke(event) + "/" + component.invoke(event), violations.invoke(event), message.invoke(event)));

        events.cancel(events.event(EVENTS + "PlayerViolationCommandEvent"));
    }

}

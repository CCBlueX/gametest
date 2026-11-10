package net.ccbluex.gametest.plugin.anticheat;

import org.bukkit.entity.Player;

import static net.ccbluex.gametest.plugin.anticheat.PluginEvents.getter;

/** Spartan's violations. The commands it punishes with never run. */
final class SpartanProbe {

    private static final String API = "ai.idealistic.spartan.api.";

    private SpartanProbe() {
    }

    static void start(PluginEvents events, Probe probe) throws ReflectiveOperationException {
        var violation = events.event(API + "PlayerViolationEvent");
        var player = getter(violation, "getPlayer");
        var hackType = getter(violation, "getHackType");
        var message = getter(violation, "getMessage");
        var hackTypes = events.type("ai.idealistic.spartan.abstraction.check.CheckEnums$HackType");
        var violations = PluginEvents.method(events.type(API + "SpartanAPI"), "getVL", Player.class, hackTypes);
        events.listen(violation, event -> {
            var flagged = (Player) player.invoke(event);
            var type = hackType.invoke(event);
            probe.flag(Anticheat.SPARTAN, flagged.getUniqueId(), type, violations.invoke(flagged, type),
                message.invoke(event));
        });

        events.cancel(events.event(API + "PlayerViolationCommandEvent"));
    }

}

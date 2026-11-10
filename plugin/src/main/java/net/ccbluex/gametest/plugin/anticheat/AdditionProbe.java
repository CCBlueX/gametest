package net.ccbluex.gametest.plugin.anticheat;

import org.bukkit.entity.Player;

import static net.ccbluex.gametest.plugin.anticheat.PluginEvents.getter;

/** AntiCheatAddition's violations. It punishes with the commands of its config, which the server leaves empty. */
final class AdditionProbe {

    private AdditionProbe() {
    }

    static void start(PluginEvents events, Probe probe) throws ReflectiveOperationException {
        var violation = events.event("de.photon.anticheataddition.events.ViolationEvent");
        var player = getter(violation, "getPlayer");
        var module = getter(violation, "getModuleId");
        var violations = getter(violation, "getVl");
        events.listen(violation, event -> probe.flag(Anticheat.ANTICHEATADDITION,
            ((Player) player.invoke(event)).getUniqueId(), module.invoke(event), violations.invoke(event)));
    }

}

package net.ccbluex.gametest.plugin.anticheat;

import org.bukkit.entity.Player;

import static net.ccbluex.gametest.plugin.anticheat.PluginEvents.getter;

/** MX's flags. It bans at the violation limit of its config, which the server sets out of reach. */
final class MxProbe {

    private MxProbe() {
    }

    static void start(PluginEvents events, Probe probe) throws ReflectiveOperationException {
        var flag = events.event("kireiko.dev.anticheat.api.events.MXFlagEvent");
        var player = getter(flag, "getPlayer");
        var check = getter(flag, "getCheck");
        var component = getter(flag, "getComponent");
        var violations = getter(flag, "getVl");
        var info = getter(flag, "getInfo");
        events.listen(flag, event -> probe.flag(Anticheat.MX, ((Player) player.invoke(event)).getUniqueId(),
            check.invoke(event) + "/" + component.invoke(event), violations.invoke(event), info.invoke(event)));
    }

}

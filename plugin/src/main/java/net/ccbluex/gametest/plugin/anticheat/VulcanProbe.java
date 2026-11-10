package net.ccbluex.gametest.plugin.anticheat;

import org.bukkit.entity.Player;

import static net.ccbluex.gametest.plugin.anticheat.PluginEvents.getter;

/** Vulcan's flags and setbacks. Its punishments never run: the test reads the flags, and a kick would cut it short. */
final class VulcanProbe {

    private static final String API = "me.frep.vulcan.api.";

    private VulcanProbe() {
    }

    static void start(PluginEvents events, Probe probe) throws ReflectiveOperationException {
        var check = events.type(API + "check.Check");
        var checkName = getter(check, "getName");
        var checkType = getter(check, "getType");
        var violations = getter(check, "getVl");

        var flag = events.event(API + "event.VulcanFlagEvent");
        var flagged = getter(flag, "getPlayer");
        var flagCheck = getter(flag, "getCheck");
        var info = getter(flag, "getInfo");
        events.listen(flag, event -> {
            var failed = flagCheck.invoke(event);
            probe.flag(Anticheat.VULCAN, ((Player) flagged.invoke(event)).getUniqueId(),
                name((String) checkName.invoke(failed), (char) checkType.invoke(failed)), violations.invoke(failed),
                info.invoke(event));
        });

        var setback = events.event(API + "event.VulcanSetbackEvent");
        var setBack = getter(setback, "getPlayer");
        events.listen(setback, event -> probe.setback(Anticheat.VULCAN, (Player) setBack.invoke(event)));

        events.cancel(events.event(API + "event.VulcanPunishEvent"));
    }

    /** Check {@code step} of type {@code a} reads StepA, one word as the report needs it. */
    private static String name(String check, char type) {
        return Character.toUpperCase(check.charAt(0)) + check.substring(1) + Character.toUpperCase(type);
    }

}

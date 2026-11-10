package net.ccbluex.gametest.plugin.anticheat;

import java.util.UUID;

import static net.ccbluex.gametest.plugin.anticheat.PluginEvents.getter;

/** FairPlay's flags. Its config decides how it punishes; the server turns its kicks and bans off. */
final class FairPlayProbe {

    private FairPlayProbe() {
    }

    static void start(PluginEvents events, Probe probe) throws ReflectiveOperationException {
        var flag = events.event("com.arkflame.fairplay.api.event.FairPlayFlagEvent");
        var player = getter(flag, "getPlayerId");
        var check = getter(flag, "getCheckName");
        var violations = getter(flag, "getCurrentViolationLevel");
        var debug = getter(flag, "getDebug");
        events.listen(flag, event -> probe.flag(Anticheat.FAIRPLAY, (UUID) player.invoke(event), check.invoke(event),
            violations.invoke(event), debug.invoke(event)));
    }

}

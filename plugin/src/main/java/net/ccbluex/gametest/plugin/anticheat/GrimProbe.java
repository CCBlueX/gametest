package net.ccbluex.gametest.plugin.anticheat;

import ac.grim.grimac.api.GrimAPIProvider;
import ac.grim.grimac.api.event.events.FlagEvent;
import ac.grim.grimac.api.event.events.GrimPlayerSetbackEvent;
import org.bukkit.plugin.Plugin;

/** Grim's flags and setbacks. Only loaded when Grim is installed. */
final class GrimProbe {

    private GrimProbe() {
    }

    static void start(Plugin owner, Probe probe) {
        var grim = GrimAPIProvider.get();
        var plugin = grim.getGrimPlugin(owner);
        var events = grim.getEventBus();
        // Grim calls these on network threads
        events.get(FlagEvent.class).onFlagSupplier(plugin, (user, check, verbose, cancelled) -> {
            probe.flag(Anticheat.GRIM, user.getUniqueId(), check.getCheckName(), check.getViolations(),
                verbose.get());
            return cancelled;
        });
        events.get(GrimPlayerSetbackEvent.class).onPlayerSetback(plugin, (user, _, x, y, z, _) ->
            probe.report(Anticheat.GRIM, user.getUniqueId(), "SETBACK", x, y, z));
    }

}

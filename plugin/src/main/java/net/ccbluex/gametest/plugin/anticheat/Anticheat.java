package net.ccbluex.gametest.plugin.anticheat;

import net.ccbluex.gametest.plugin.TestPlayers;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * What watches a test player: the letter after {@code GT} in its name. All of them are installed side by side, each
 * in a {@link Sandbox} that shows it only the players it watches.
 */
public enum Anticheat {

    VANILLA('V', null, false),
    GRIM('G', "GrimAC", false),
    INTAVE('I', "Intave", false),
    // Sets players up while they log in
    VULCAN('U', "Vulcan", true),
    MATRIX('M', "Matrix", false),
    SPARTAN('S', "Spartan", false),
    MX('X', "MX", false),
    ANTICHEATADDITION('A', "AntiCheatAddition", false),
    FAIRPLAY('F', "FairPlay", false);

    private final char letter;
    private final @Nullable String pluginName;
    private final boolean setsUpOnLogin;

    Anticheat(char letter, @Nullable String pluginName, boolean setsUpOnLogin) {
        this.letter = letter;
        this.pluginName = pluginName;
        this.setsUpOnLogin = setsUpOnLogin;
    }

    /** The test player's anticheat, or null for anyone else. */
    public static @Nullable Anticheat of(Player player) {
        if (!TestPlayers.matches(player)) {
            return null;
        }
        var letter = player.getName().charAt(2);
        for (var anticheat : values()) {
            if (anticheat.letter == letter) {
                return anticheat;
            }
        }
        return null;
    }

    /** The plugin, or null for Vanilla or when it is not installed. */
    @Nullable Plugin plugin() {
        if (pluginName == null) {
            return null;
        }
        var plugin = Bukkit.getPluginManager().getPlugin(pluginName);
        return plugin != null && plugin.isEnabled() ? plugin : null;
    }

    /** Whether the anticheat has to see a player log in to check it later. */
    boolean setsUpOnLogin() {
        return setsUpOnLogin;
    }

    public boolean installed() {
        return pluginName == null || plugin() != null;
    }

    /** Name and version, as the harness reports which anticheat a test ran against. */
    public String describe() {
        if (pluginName == null) {
            return "Vanilla";
        }
        var plugin = plugin();
        return plugin == null ? pluginName : pluginName + " " + plugin.getPluginMeta().getVersion();
    }

}

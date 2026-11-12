package net.ccbluex.gametest.plugin.anticheat;

import net.ccbluex.gametest.plugin.TestPlayers;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * What watches a test player: the letters between {@code GT} and {@code _} in its name, one per anticheat. All of
 * them are installed side by side, each in a {@link Sandbox} that shows it only the players it watches.
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

    /** The test player's anticheats; empty for anyone else, or when one of its letters stands for none. */
    public static Set<Anticheat> of(Player player) {
        var anticheats = EnumSet.noneOf(Anticheat.class);
        if (!TestPlayers.matches(player)) {
            return anticheats;
        }
        for (var letter : letters(player).toCharArray()) {
            var anticheat = byLetter(letter);
            if (anticheat == null) {
                return EnumSet.noneOf(Anticheat.class);
            }
            anticheats.add(anticheat);
        }
        return anticheats;
    }

    /** The letters a test player's name asks for. */
    public static String letters(Player player) {
        var name = player.getName();
        return name.substring(2, name.indexOf('_'));
    }

    public static @Nullable Anticheat byLetter(char letter) {
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

    /** The plugin's name, as reports name the anticheat they come from. */
    public String label() {
        return pluginName == null ? "Vanilla" : pluginName;
    }

    /** Name and version, as the harness reports which anticheat a test ran against. */
    public String describe() {
        var plugin = plugin();
        return plugin == null ? label() : label() + " " + plugin.getPluginMeta().getVersion();
    }

}

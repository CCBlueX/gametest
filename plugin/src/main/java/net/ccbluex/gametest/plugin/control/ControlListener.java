package net.ccbluex.gametest.plugin.control;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.ccbluex.gametest.plugin.TestPlayers;
import net.ccbluex.gametest.plugin.session.Sessions;
import net.ccbluex.gametest.plugin.session.WorldSpec;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;

/** What the client harness asks for on {@code gametest:control}: one JSON object per payload. */
public final class ControlListener implements PluginMessageListener {

    private final Sessions sessions;

    public ControlListener(Sessions sessions) {
        this.sessions = sessions;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] payload) {
        if (!TestPlayers.matches(player)) {
            return;
        }
        try {
            var message = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
            handle(player, message);
        } catch (RuntimeException exception) {
            // Gson and the handlers throw on anything that is not the message they expect
            Control.error(player, "Malformed message: " + exception.getMessage());
        }
    }

    private void handle(Player player, JsonObject message) {
        var type = WorldSpec.string(message, "type", "");
        switch (type) {
            case "open" -> sessions.open(player, message);
            case "command" -> sessions.command(player, message);
            case "close" -> sessions.close(player);
            case "drop" -> sessions.drop(player);
            default -> Control.error(player, "Unknown type '" + type + "'");
        }
    }

}

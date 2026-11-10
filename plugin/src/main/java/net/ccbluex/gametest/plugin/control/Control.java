package net.ccbluex.gametest.plugin.control;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.ccbluex.gametest.plugin.anticheat.Anticheat;
import net.ccbluex.gametest.plugin.session.Session;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.Identifier;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * What the server tells the client harness on the plugin channel {@code gametest:control}: one JSON object per
 * payload. Safe to call from any thread.
 */
public final class Control {

    public static final String CHANNEL = "gametest:control";
    private static final Identifier ID = Identifier.fromNamespaceAndPath("gametest", "control");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    /** A payload ends at 1 MiB and JSON can escape one character into six bytes. */
    private static final int MAX_TEXT = 100_000;

    private Control() {
    }

    public static void ready(Player player, Session session, boolean resumed, Anticheat anticheat) {
        send(player, "ready", message -> {
            message.addProperty("session", session.id());
            message.addProperty("resumed", resumed);
            message.addProperty("server", Bukkit.getName() + " " + Bukkit.getVersion());
            message.addProperty("anticheat", anticheat.describe());
        });
    }

    public static void busy(Player player, int retryAfter) {
        send(player, "busy", message -> message.addProperty("retryAfter", retryAfter));
    }

    public static void output(Player player, int id, String line) {
        send(player, "output", message -> {
            message.addProperty("id", id);
            message.addProperty("message", shortened(line));
        });
    }

    public static void done(Player player, int id, int result) {
        send(player, "done", message -> {
            message.addProperty("id", id);
            message.addProperty("result", result);
        });
    }

    public static void report(Player player, String report, String text) {
        send(player, "report", message -> {
            message.addProperty("report", report);
            message.addProperty("message", shortened(text));
        });
    }

    static void log(Player player, String level, String line) {
        send(player, "log", message -> {
            message.addProperty("level", level);
            message.addProperty("message", shortened(line));
        });
    }

    /** The answer to {@code close}. A client that quits before it has seen this may have quit before its close was read. */
    public static void closed(Player player) {
        send(player, "closed", _ -> { });
    }

    public static void error(Player player, String text) {
        send(player, "error", message -> message.addProperty("message", shortened(text)));
    }

    /** A refused command: the error names it, and the {@code done} that follows ends it for whoever waits. */
    public static void refused(Player player, int id, String text) {
        send(player, "error", message -> {
            message.addProperty("id", id);
            message.addProperty("message", shortened(text));
        });
        done(player, id, 0);
    }

    private static void send(Player player, String type, Consumer<JsonObject> fields) {
        var message = new JsonObject();
        message.addProperty("type", type);
        fields.accept(message);
        var payload = new DiscardedPayload(ID, GSON.toJson(message).getBytes(StandardCharsets.UTF_8));
        // Bukkit's sendPluginMessage would drop this for a client that has not registered the channel
        ((CraftPlayer) player).getHandle().connection.send(new ClientboundCustomPayloadPacket(payload));
    }

    private static String shortened(String text) {
        return text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT);
    }

}

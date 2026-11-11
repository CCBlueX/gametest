package net.ccbluex.gametest.plugin.session;

import net.ccbluex.gametest.plugin.control.Control;
import net.kyori.adventure.text.Component;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.command.ServerCommandSender;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

/**
 * Who runs a session's commands: a console whose feedback goes to the harness as {@code output} lines instead
 * of the server log, which counts what the command returned and collects what it started that later commands have
 * to wait for.
 */
final class SessionConsole implements CommandSource, CommandResultCallback {

    private static final CommandSender SENDER = new Sender();

    private final Session session;
    private final Player player;
    private final int command;
    private int results;
    private int successes;
    private int lastResult;
    private CompletableFuture<?> work = CompletableFuture.completedFuture(null);

    SessionConsole(Session session, Player player, int command) {
        this.session = session;
        this.player = player;
        this.command = command;
    }

    @Override
    public void sendSystemMessage(net.minecraft.network.chat.Component message) {
        session.withShownNames(message.getString()).lines().forEach(line -> Control.output(player, command, line));
    }

    @Override
    public boolean acceptsSuccess() {
        return true;
    }

    @Override
    public boolean acceptsFailure() {
        return true;
    }

    @Override
    public boolean shouldInformAdmins() {
        return false;
    }

    @Override
    public CommandSender getBukkitSender(CommandSourceStack stack) {
        return SENDER;
    }

    @Override
    public void onResult(boolean success, int result) {
        results++;
        if (success) {
            successes++;
            lastResult = result;
        }
    }

    /** Brigadier's return value: what the command returned, or how many of its forks succeeded. */
    int result() {
        return results == 1 ? lastResult : successes;
    }

    /** Holds back the session's next commands until {@code started} is done. */
    void await(CompletableFuture<?> started) {
        work = CompletableFuture.allOf(work, started);
    }

    CompletableFuture<?> work() {
        return work;
    }

    /**
     * Vanilla commands also pass for whoever holds their Bukkit permission. A sender that is no operator holds
     * only the ones every player has, which leaves the permission level of the command source in charge.
     */
    private static final class Sender extends ServerCommandSender {

        @Override
        public void sendMessage(String message) {
        }

        @Override
        public void sendMessage(String... messages) {
        }

        @Override
        public String getName() {
            return "Server";
        }

        @Override
        public Component name() {
            return Component.text(getName());
        }

        @Override
        public boolean isOp() {
            return false;
        }

        @Override
        public void setOp(boolean value) {
            throw new UnsupportedOperationException("The console of a game test session is never an operator");
        }

    }

}

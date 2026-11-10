package net.ccbluex.gametest.plugin;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.ccbluex.gametest.plugin.session.Sessions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;

/** {@code /watch} lists the running sessions for spectators, {@code /watch <player>} takes one there. */
public final class WatchCommand {

    public static final String NAME = "watch";

    private WatchCommand() {
    }

    static LiteralCommandNode<CommandSourceStack> create(Sessions sessions) {
        return Commands.literal(NAME)
            .requires(source -> source.getSender() instanceof Player player && !TestPlayers.matches(player))
            .executes(context -> list(sessions, (Player) context.getSource().getSender()))
            .then(Commands.argument("player", StringArgumentType.word())
                .suggests((_, builder) -> {
                    sessions.all().forEach(session -> builder.suggest(session.playerName()));
                    return builder.buildFuture();
                })
                .executes(context -> watch(
                    sessions, (Player) context.getSource().getSender(), StringArgumentType.getString(context, "player")
                )))
            .build();
    }

    private static int list(Sessions sessions, Player spectator) {
        if (sessions.all().isEmpty()) {
            spectator.sendMessage(Component.text("No game test is running.", NamedTextColor.GRAY));
        }
        for (var session : sessions.all()) {
            var info = session.info();
            var test = " " + info.repository() + " " + info.run() + " " + info.test() + " ";
            spectator.sendMessage(Component.text()
                .append(Component.text(session.playerName(), NamedTextColor.AQUA))
                .append(Component.text(test, NamedTextColor.WHITE))
                .append(Component.text(age(session.age()), NamedTextColor.GRAY))
                .clickEvent(ClickEvent.runCommand("/" + NAME + " " + session.playerName())));
        }
        return sessions.all().size();
    }

    private static int watch(Sessions sessions, Player spectator, String name) {
        var session = sessions.all().stream()
            .filter(candidate -> candidate.playerName().equalsIgnoreCase(name))
            .findFirst().orElse(null);
        if (session == null) {
            spectator.sendMessage(Component.text(name + " has no session.", NamedTextColor.RED));
            return 0;
        }
        var player = Bukkit.getPlayer(session.playerId());
        var inSession = player != null && session.owns(player.getWorld());
        spectator.teleport(inSession ? player.getLocation() : session.overworld().getSpawnLocation());
        return 1;
    }

    private static String age(Duration age) {
        return age.toMinutes() + ":" + String.format("%02d", age.toSecondsPart());
    }

}

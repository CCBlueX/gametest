package net.ccbluex.gametest.plugin.control;

import net.ccbluex.gametest.plugin.TestPlayers;
import net.ccbluex.gametest.plugin.session.Sessions;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Sends every line of the server log that names a test player to that player's harness, the way the console
 * prints it. Log events come from any thread.
 */
public final class LogForwarder extends AbstractAppender {

    /** Loggers the console prints without their name in front, as in Paper's log4j2.xml. */
    private static final String[] UNNAMED_LOGGERS =
        {"net.minecraft.", "Minecraft", "com.mojang.", "com.sk89q.", "ru.tehkode."};
    private static final Pattern FORMATTING =
        Pattern.compile("\u001B\\[[;\\d]*m|§[0-9a-fk-orx]", Pattern.CASE_INSENSITIVE);

    private final TestPlayers players;
    private final Sessions sessions;
    private final String ownLogger;
    private final ThreadLocal<Boolean> forwarding = ThreadLocal.withInitial(() -> false);

    private LogForwarder(TestPlayers players, Sessions sessions, String ownLogger) {
        super("GameTestLogForwarder", null, null, true, Property.EMPTY_ARRAY);
        this.players = players;
        this.sessions = sessions;
        this.ownLogger = ownLogger;
    }

    public static LogForwarder start(TestPlayers players, Sessions sessions, String ownLogger) {
        var forwarder = new LogForwarder(players, sessions, ownLogger);
        forwarder.start();
        ((Logger) LogManager.getRootLogger()).addAppender(forwarder);
        return forwarder;
    }

    public void shutDown() {
        ((Logger) LogManager.getRootLogger()).removeAppender(this);
        stop();
    }

    @Override
    public void append(LogEvent event) {
        var level = level(event.getLevel());
        // What this plugin logs about a session is for the server's operators
        var own = ownLogger.equals(event.getLoggerName()) && event.getLevel() == Level.INFO;
        if (level == null || own || players.online().isEmpty() || forwarding.get()) {
            return;
        }

        forwarding.set(true);
        try {
            var line = line(event);
            for (var player : players.online()) {
                if (mentions(line, player.getName())) {
                    var session = sessions.of(player);
                    Control.log(player, level, session == null ? line : session.withShownNames(line));
                }
            }
        } finally {
            forwarding.set(false);
        }
    }

    /** The name on its own, not as the start of a longer one: {@code GTV_a} must not hear about {@code GTV_ab}. */
    private static boolean mentions(String line, String name) {
        for (var at = line.indexOf(name); at >= 0; at = line.indexOf(name, at + 1)) {
            var end = at + name.length();
            var alone = (at == 0 || !isNameChar(line.charAt(at - 1)))
                && (end == line.length() || !isNameChar(line.charAt(end)));
            if (alone) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNameChar(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }

    private static String level(Level level) {
        if (level.isMoreSpecificThan(Level.ERROR)) {
            return "ERROR";
        }
        return level == Level.WARN ? "WARN" : level == Level.INFO ? "INFO" : null;
    }

    private static String line(LogEvent event) {
        var logger = event.getLoggerName();
        var named = logger != null && !logger.isEmpty() && Stream.of(UNNAMED_LOGGERS).noneMatch(logger::startsWith);
        var message = FORMATTING.matcher(event.getMessage().getFormattedMessage()).replaceAll("");
        var line = named ? "[" + logger + "] " + message : message;
        return event.getThrown() == null ? line : line + "\n" + event.getThrown();
    }

}

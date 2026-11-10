package net.ccbluex.gametest.plugin.anticheat;

import org.bukkit.Bukkit;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * The events of an anticheat that publishes no API artifact, found by name in the installed plugin. Most of them are
 * paid, so the plugin cannot compile against them.
 */
final class PluginEvents {

    private final Plugin owner;
    private final ClassLoader loader;

    PluginEvents(Plugin owner, Plugin anticheat) {
        this.owner = owner;
        this.loader = anticheat.getClass().getClassLoader();
    }

    Class<? extends Event> event(String name) throws ClassNotFoundException {
        return type(name).asSubclass(Event.class);
    }

    Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(name, true, loader);
    }

    /** Runs {@code handler} for every event of {@code type} that is not cancelled, on whatever thread raises it. */
    void listen(Class<? extends Event> type, Handler handler) {
        Bukkit.getPluginManager().registerEvent(type, new Listener() { }, EventPriority.MONITOR, (_, event) -> {
            if (!type.isInstance(event)) {
                return;
            }
            try {
                handler.handle(event);
            } catch (Throwable failure) {
                throw new EventException(failure);
            }
        }, owner, true);
    }

    /** Cancels every event of {@code type}, such as the commands an anticheat punishes with. */
    void cancel(Class<? extends Event> type) {
        Bukkit.getPluginManager().registerEvent(type, new Listener() { }, EventPriority.LOWEST, (_, event) -> {
            if (type.isInstance(event)) {
                ((Cancellable) event).setCancelled(true);
            }
        }, owner, false);
    }

    /** A public method without parameters, as a handle that takes and returns Object. */
    static MethodHandle getter(Class<?> type, String name) throws ReflectiveOperationException {
        return MethodHandles.publicLookup().unreflect(type.getMethod(name))
            .asType(MethodType.methodType(Object.class, Object.class));
    }

    /** A public static method. */
    static MethodHandle method(Class<?> type, String name, Class<?>... parameters)
        throws ReflectiveOperationException {
        return MethodHandles.publicLookup().unreflect(type.getMethod(name, parameters));
    }

    interface Handler {
        void handle(Event event) throws Throwable;
    }

}

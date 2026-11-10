package net.ccbluex.gametest.plugin.session;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.minecraft.world.level.levelgen.WorldOptions;
import org.jspecify.annotations.Nullable;

/** The overworld a session asked for in {@code open}. Its Nether and End share the seed and the structures. */
public record WorldSpec(Type type, long seed, String generatorSettings, boolean structures) {

    private static final String VOID_SETTINGS =
        "{\"biome\":\"minecraft:the_void\",\"layers\":[{\"block\":\"minecraft:air\",\"height\":1}]}";

    enum Type { FLAT, NORMAL, VOID }

    /** @throws IllegalArgumentException with what is wrong about the request */
    static WorldSpec parse(@Nullable JsonObject world) {
        if (world == null) {
            world = new JsonObject();
        }
        var type = switch (string(world, "type", "flat")) {
            case "flat" -> Type.FLAT;
            case "normal" -> Type.NORMAL;
            case "void" -> Type.VOID;
            default -> throw new IllegalArgumentException("world.type must be flat, normal or void");
        };
        var seed = WorldOptions.parseSeed(string(world, "seed", "")).orElseGet(WorldOptions::randomSeed);
        var settings = string(world, "generatorSettings", type == Type.VOID ? VOID_SETTINGS : "");
        try {
            if (!settings.isEmpty() && !JsonParser.parseString(settings).isJsonObject()) {
                throw new IllegalArgumentException("world.generatorSettings must hold a JSON object");
            }
        } catch (JsonSyntaxException exception) {
            throw new IllegalArgumentException("world.generatorSettings is not JSON: " + exception.getMessage());
        }
        var structures = world.has("structures") && world.get("structures").getAsBoolean();
        return new WorldSpec(type, seed, settings, structures);
    }

    public static String string(JsonObject object, String field, String fallback) {
        JsonElement value = object.get(field);
        return value == null || value.isJsonNull() ? fallback : value.getAsString();
    }

}

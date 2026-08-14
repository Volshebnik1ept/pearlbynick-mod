package dev.pearlowner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранит соответствие UUID <-> ник, полученное из таб-листа (то есть тот
 * UUID, который реально выдаёт сервер, а не публичный Mojang UUID — на
 * офлайн-серверах это будет offline-UUID). Данные переживают перезапуск
 * игры (JSON-файл в config/).
 *
 * Этот класс не трогает MC API, поэтому при смене версии игры (в т.ч.
 * переходе на 26.2) менять здесь ничего не нужно.
 */
public final class PlayerUuidCache {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("pearlowner_players.json");

    private static final Map<UUID, String> uuidToName = new ConcurrentHashMap<>();
    private static final Map<String, UUID> nameToUuid = new ConcurrentHashMap<>();

    private PlayerUuidCache() {}

    public static void load() {
        if (!Files.exists(FILE)) return;
        try {
            String json = Files.readString(FILE);
            Type type = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> raw = GSON.fromJson(json, type);
            if (raw == null) return;
            for (Map.Entry<String, String> e : raw.entrySet()) {
                try {
                    UUID uuid = UUID.fromString(e.getKey());
                    uuidToName.put(uuid, e.getValue());
                    nameToUuid.put(e.getValue().toLowerCase(), uuid);
                } catch (IllegalArgumentException ignored) {
                    // битая строка в файле — пропускаем
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Map<String, String> raw = new HashMap<>();
            for (Map.Entry<UUID, String> e : uuidToName.entrySet()) {
                raw.put(e.getKey().toString(), e.getValue());
            }
            Files.writeString(FILE, GSON.toJson(raw));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Вызывается при каждом обновлении таб-листа. */
    public static void update(UUID uuid, String name) {
        if (uuid == null || name == null) return;
        String prev = uuidToName.put(uuid, name);
        nameToUuid.put(name.toLowerCase(), uuid);
        if (!name.equals(prev)) {
            save();
        }
    }

    public static String getName(UUID uuid) {
        return uuidToName.get(uuid);
    }

    public static UUID getUuid(String name) {
        return nameToUuid.get(name.toLowerCase());
    }
}

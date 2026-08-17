package org.chivqsss;

import org.chivqsss.commands.CommandTargetObject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class Storage implements CommandTargetObject {
    public static final Logger LOGGER = Logger.getLogger("CDB");
    private final Engine ENGINE;

    public Storage(Path path) {
        this.ENGINE = new Engine(
                1024,
                0.75f,
                16, //5 * 1024 * 1024,
                5 * 1024 * 1024,
                path);
    }

    public Storage() {
        this.ENGINE = new Engine(
                1024,
                0.75f,
                5 * 1024 * 1024,
                5 * 1024 * 1024,
                Engine.resolveDefaultDataDir());
    }

    @Override
    public void put(String key, byte[] value, long ttl) {
        ENGINE.put(key, value, ttl);
    }

    @Override
    public void delete(String key) {
        ENGINE.remove(key);
    }

    public Optional<byte[]> get(String key) {
        return ENGINE.get(key);
    }
}

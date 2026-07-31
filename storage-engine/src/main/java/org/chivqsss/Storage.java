package org.chivqsss;

import org.chivqsss.commands.CommandTargetObject;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class Storage implements CommandTargetObject {
    private final ConcurrentHashMap<String, byte[]> STORAGE = new ConcurrentHashMap<>();
    public final Logger LOGGER = Logger.getLogger("DB");

    @Override
    public void put(String key, byte[] value, long ttl) {
        STORAGE.put(key, value);
    }

    @Override
    public void delete(String key) {
        if (STORAGE.remove(key) == null) {
            LOGGER.warning("No value is present for key: " + key);
        }
    }

    public Optional<byte[]> get(String key) {
        return Optional.ofNullable(STORAGE.get(key));
    }
}

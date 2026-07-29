package org.chivqsss;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class Storage {
    private final ConcurrentHashMap<String, byte[]> STORAGE = new ConcurrentHashMap<>();
    public final Logger LOGGER = Logger.getLogger("DB");

    public void put(String key, byte[] value) {
        STORAGE.put(key, value);
    }

    public void delete(String key) {
        if (STORAGE.remove(key) == null) {
            LOGGER.warning("No value is present for key: " + key);
        }
    }

    public byte[] get(String key) {
        return STORAGE.getOrDefault(key, null);
    }
}

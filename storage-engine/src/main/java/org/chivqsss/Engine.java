package org.chivqsss;

import org.chivqsss.disc.WriteAheadLog;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

public class Engine {
    private final int CACHE_MAX_SIZE; // = 5 * 1024 * 1024;
    private final LinkedHashMap<String, byte[]> CACHE;
    private WriteAheadLog wal = null;
    private Path dataDir = null;


    public Engine(int initialCapacity, float loadFactor, int cacheMaxSize, Path dataDir) {
        this.CACHE_MAX_SIZE = cacheMaxSize;
        this.CACHE = new LinkedHashMap<>(initialCapacity, loadFactor, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                return size() > CACHE_MAX_SIZE;
            }
        };
        try {
            this.dataDir = dataDir;
            Files.createDirectories(dataDir);
            Path dataFile = dataDir.resolve("conqydb.log");
            this.wal = new WriteAheadLog(dataFile);
        } catch (Exception e) {
            Storage.LOGGER.warning(e.toString());
        }
    }

    public static Path resolveDefaultDataDir() {
        String xdgData = System.getenv("XDG_DATA_HOME"); // I use arch btw™
        Path base = (xdgData != null)
                ? Path.of(xdgData)
                : Path.of(System.getProperty("user.home"), ".local", "share");
        return base.resolve("conqydb");
    }

    public void put(String key, byte[] value, long ttl) {
        CACHE.put(key, value);
        // put to drive
        wal.put(key, value, ttl);
        Storage.LOGGER.info("PUT: " + key + " " + new String(value, StandardCharsets.UTF_8));
    }

    public Optional<byte[]> get(String key) {
        if (CACHE.containsKey(key)) {
            return Optional.ofNullable(CACHE.get(key));
        }
        Optional<byte[]> fromDisk = wal.get(key);
        fromDisk.ifPresent(v -> CACHE.put(key, v));
        Storage.LOGGER.info("GET: " + key);
        return fromDisk;
    }

    public void remove(String key) {
        CACHE.remove(key);
        // remove from drive
        wal.remove(key);
    }
}

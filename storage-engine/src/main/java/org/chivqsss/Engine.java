package org.chivqsss;

import org.chivqsss.disc.WriteAheadLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Engine {
    private final int CACHE_MAX_SIZE; // = 5 * 1024 * 1024;
    private final LinkedHashMap<String, byte[]> CACHE;
    private final WriteAheadLog wal;
    private final Path dataDir;


    public Engine(int initialCapacity, float loadFactor, int cacheMaxSize, Path dataDir) throws IOException {
        this.CACHE_MAX_SIZE = cacheMaxSize;
        this.CACHE = new LinkedHashMap<>(initialCapacity, loadFactor, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                return size() > CACHE_MAX_SIZE;
            }
        };

        this.dataDir = dataDir;
        Files.createDirectories(dataDir);
        Path dataFile = dataDir.resolve("conqydb.log");
        this.wal = new WriteAheadLog(dataFile);
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
    }

    public Optional<byte[]> get(String key) {
        if (CACHE.containsKey(key)) {
            return Optional.ofNullable(CACHE.get(key));
        }
        Optional<byte[]> fromDisk = wal.get(key);
        fromDisk.ifPresent(v -> CACHE.put(key, v));
        return fromDisk;
    }

    public void remove(String key) {
        CACHE.remove(key);
        // remove from drive
        //wal.remove(key);
    }
}

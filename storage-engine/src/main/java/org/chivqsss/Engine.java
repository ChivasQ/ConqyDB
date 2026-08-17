package org.chivqsss;

import org.chivqsss.disc.WriteAheadLog;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

public class Engine {
    private final int CACHE_MAX_SIZE; // = 5 * 1024 * 1024;
    private final int MAX_BYTES_BEFORE_FLUSH;
    private int BYTES_BEFORE_FLUSH;
    private final LinkedHashMap<String, byte[]> CACHE;
    private WriteAheadLog wal;
    private Path dataDir;
    private ConcurrentSkipListMap<String, byte[]> memTable;


    public Engine(int initialCapacity, float loadFactor, int cacheMaxSize, int maxBytesBeforeFlush, Path dataDir) {
        this.CACHE_MAX_SIZE = cacheMaxSize;
        this.MAX_BYTES_BEFORE_FLUSH = maxBytesBeforeFlush;
        this.BYTES_BEFORE_FLUSH = 0;

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
            this.wal.rebuildIndex();
            this.memTable = new ConcurrentSkipListMap<>(Comparator.naturalOrder());
            this.wal.replayInto((s, bytes) -> memTable.put(s, bytes));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Path resolveDefaultDataDir() {
        String xdgData = System.getenv("XDG_DATA_HOME"); // I use arch btw™
        Path base = (xdgData != null)
                ? Path.of(xdgData)
                : Path.of(System.getProperty("user.home"), ".local", "share");
        return base.resolve("conqydb");
    }

    public synchronized void put(String key, byte[] value, long ttl) {
        CACHE.put(key, value);
        // put to drive
        wal.put(key, value, ttl);
        memTable.put(key, value);
        this.BYTES_BEFORE_FLUSH += value.length;
        Storage.LOGGER.info("PUT: " + key + " " + new String(value, StandardCharsets.UTF_8));
    }

    public synchronized Optional<byte[]> get(String key) {
        if (CACHE.containsKey(key)) {
            Storage.LOGGER.info("GET FROM CACHE: " + key);
            return Optional.ofNullable(CACHE.get(key));
        } else if (memTable.containsKey(key)) {
            byte[] value = memTable.get(key);
            CACHE.put(key, value); // for access order
            Storage.LOGGER.info("GET FROM MEMTABLE: " + key);
            return Optional.ofNullable(value);
        } else {
            Storage.LOGGER.info("GET FROM WAL: " + key); // idk if needed
            return wal.get(key);
        }
    }

    public void remove(String key) {
        CACHE.remove(key);
        // remove from drive
        wal.remove(key);
        memTable.remove(key);
    }

    public void checkForFlush() {
        if (BYTES_BEFORE_FLUSH >= MAX_BYTES_BEFORE_FLUSH) {

        }
    }
}

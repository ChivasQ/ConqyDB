package org.chivqsss;

import org.chivqsss.disc.WriteAheadLog;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

public class Engine {
    private final int CACHE_MAX_SIZE; // = 5 * 1024 * 1024;
    private final int MAX_BYTES_BEFORE_FLUSH;
    private AtomicLong BYTES_BEFORE_FLUSH;
    private final ReentrantLock putFlushLock = new ReentrantLock();
    private final LinkedHashMap<String, byte[]> CACHE;
    private WriteAheadLog wal;
    private Path dataDir;
    private ConcurrentSkipListMap<String, byte[]> memTable;


    public Engine(int initialCapacity, float loadFactor, int cacheMaxSize, int maxBytesBeforeFlush, Path dataDir) {
        this.CACHE_MAX_SIZE = cacheMaxSize;
        this.MAX_BYTES_BEFORE_FLUSH = maxBytesBeforeFlush;
        this.BYTES_BEFORE_FLUSH = new AtomicLong(0);

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

    public void put(String key, byte[] value, long ttl) {
        putFlushLock.lock();
        try {
            CACHE.put(key, value);
            // put to drive
            wal.put(key, value, ttl);
            memTable.put(key, value);
            this.BYTES_BEFORE_FLUSH.addAndGet(value.length);

            checkForFlush();

            Storage.LOGGER.info("PUT: " + key + " " + new String(value, StandardCharsets.UTF_8));
        } finally {
            putFlushLock.unlock();
        }
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
        if (BYTES_BEFORE_FLUSH.get() >= MAX_BYTES_BEFORE_FLUSH) {
            flush();
        }
    }

    private void flush() {
        LocalDateTime date = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm-ss");
        Storage.LOGGER.info("Flush time! " + date.format(formatter));

        Thread thread = new Thread(() -> {
            putFlushLock.lock();
            try {
                // creating new copy with already compacted index
                Path new_log_path = dataDir.resolve("wal-flush-" + date.format(formatter) + ".log");
                WriteAheadLog new_log = new WriteAheadLog(new_log_path);
                this.wal.replayInto(((s, bytes) -> new_log.put(s, bytes, 0)));

                // copy log and assigning to original wal
                Files.copy(new_log_path, wal.getDataFile(), StandardCopyOption.REPLACE_EXISTING);
                this.wal.updateIndex(new_log.getIndex());

                BYTES_BEFORE_FLUSH.set(0);
                Storage.LOGGER.info("Flush end " + date.format(formatter));
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                putFlushLock.unlock();
            }
        });
        thread.start();
    }
}

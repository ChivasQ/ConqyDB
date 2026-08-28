package org.chivqsss;

import org.chivqsss.disc.SSTable;
import org.chivqsss.disc.WriteAheadLog;
import org.chivqsss.utils.FileUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

import static org.chivqsss.utils.FileUtils.parseSeqFromName;

public class Engine {
    private final int CACHE_MAX_SIZE; // = 5 * 1024 * 1024;
    private final int MAX_BYTES_BEFORE_FLUSH;
    private AtomicLong BYTES_BEFORE_FLUSH;
    private AtomicLong SSTABLE_COUNTER;
    private final ReentrantLock putFlushLock = new ReentrantLock();
    private final LinkedHashMap<String, byte[]> CACHE;
    private WriteAheadLog wal;
    private Path dataDir;
    private ConcurrentSkipListMap<String, byte[]> memTable;
    private final CopyOnWriteArrayList<Path> sstablesList = new CopyOnWriteArrayList<>();
    private volatile ConcurrentSkipListMap<String, byte[]> flushingMemTable = null;
    private static final byte[] TOMBSTONE = new byte[0];


    public Engine(int initialCapacity, float loadFactor, int cacheMaxSize, int maxBytesBeforeFlush, Path dataDir) {
        this.CACHE_MAX_SIZE = cacheMaxSize;
        this.MAX_BYTES_BEFORE_FLUSH = maxBytesBeforeFlush;
        this.BYTES_BEFORE_FLUSH = new AtomicLong(0);
        long maxSeq = Arrays.stream(FileUtils.findFilesSorted(dataDir, "sst"))
                .mapToLong(f -> parseSeqFromName(f.getName()).orElse(-1))
                .max()
                .orElse(-1);
        SSTABLE_COUNTER = new AtomicLong(maxSeq + 1);
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
            this.memTable = new ConcurrentSkipListMap<>(FileUtils.utf8Comparator);
            this.wal.replayInto((s, bytes) -> memTable.put(s, bytes));

            File[] initialFiles = FileUtils.findFilesSorted(dataDir, "sst");
            for (File f : initialFiles) {
                sstablesList.add(f.toPath());
            }

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

    public Optional<byte[]> get(String key) {
        putFlushLock.lock();
        try {
            if (CACHE.containsKey(key)) {
                Storage.LOGGER.info("GET FROM CACHE: " + key);
                byte[] cached = CACHE.get(key);
                return cached == TOMBSTONE ? Optional.empty() : Optional.of(cached);
            }
            if (memTable.containsKey(key)) {
                byte[] value = memTable.get(key);
                if (value == TOMBSTONE) return Optional.empty();
                CACHE.put(key, value); // for access order
                Storage.LOGGER.info("GET FROM MEMTABLE: " + key);
                return Optional.ofNullable(value);
            }

            ConcurrentSkipListMap<String, byte[]> flushing = this.flushingMemTable;
            if (flushing != null && flushing.containsKey(key)) {
                byte[] value = flushing.get(key);
                if (value == TOMBSTONE) return Optional.empty();
                CACHE.put(key, value);
                Storage.LOGGER.info("GET FROM FLUSHING MEMTABLE: " + key);
                return Optional.ofNullable(value);
            }

            Storage.LOGGER.info("GET FROM SSTABLE: " + key);
            Optional<byte[]> fromDisk = SSTable.getFromDrive(key, sstablesList);
            if (fromDisk.isPresent()) {
                byte[] val = fromDisk.get();
                if (val.length == 0) return Optional.empty();

                CACHE.put(key, val);
                return Optional.of(val);
            }

            return fromDisk;
        } finally {
            putFlushLock.unlock();
        }
    }

    public void remove(String key) {
        putFlushLock.lock();
        try {
            CACHE.remove(key);
            wal.remove(key);

            memTable.put(key, TOMBSTONE);

            this.BYTES_BEFORE_FLUSH.addAndGet(TOMBSTONE.length);
            checkForFlush();
        } finally {
            putFlushLock.unlock();
        }
    }

    public void checkForFlush() {
        if (BYTES_BEFORE_FLUSH.get() >= MAX_BYTES_BEFORE_FLUSH && flushingMemTable == null) {
            flush(); // under lock from put
        }
    }

    private void flush() {
        LocalDateTime date = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm-ss");
        Storage.LOGGER.info("Flush time! " + date.format(formatter));
        final ConcurrentSkipListMap<String, byte[]> toFlush = this.memTable; // pointing data to toFlush var in same thread, UNDER LOCK FROM PUT
        this.flushingMemTable = toFlush;
        this.memTable = new ConcurrentSkipListMap<>(FileUtils.utf8Comparator); // replacing with new memtable in same thread, UNDER LOCK

        final WriteAheadLog oldWal = this.wal;
        Path newWalPath = dataDir.resolve("conqydb-" + date.format(formatter) + ".log");
        try {
            this.wal = new WriteAheadLog(newWalPath);
        }  catch (Exception e) {
            Storage.LOGGER.severe("Failed to init WAL: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        BYTES_BEFORE_FLUSH.set(0);

        Thread thread = new Thread(() -> {
            try {
                Path sstablePath = nextSSTablePath(dataDir, SSTABLE_COUNTER.getAndIncrement());
                SSTable.writeToDisk(toFlush, sstablePath);
                Storage.LOGGER.info("SSTable successfully saved: " + sstablePath.getFileName());
                sstablesList.addFirst(sstablePath);
                this.flushingMemTable = null;
                Files.deleteIfExists(oldWal.getDataFile());
                Storage.LOGGER.info("Old WAL exterminated");
            } catch (Exception e) {
                Storage.LOGGER.severe("Failed to write SSTable: " + e.getMessage());
                e.printStackTrace();
                this.flushingMemTable = null;
            }
        });
        thread.start();
    }

    public static Path nextSSTablePath(Path dir, long sequenceNumber) {
        return dir.resolve(String.format("sstable_%019d.sst", sequenceNumber));
    }
}

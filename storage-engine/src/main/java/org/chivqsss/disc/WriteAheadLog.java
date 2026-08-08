package org.chivqsss.disc;

import org.chivqsss.Storage;
import org.chivqsss.commands.CommandCodes;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class WriteAheadLog {
    private final Path dataFile;
    private FileChannel channel;
    private final ConcurrentHashMap<String, DiscIndexEntry> index = new ConcurrentHashMap<>();
    private final int HEADER_BYTES = Byte.BYTES + Integer.BYTES*2 + Long.BYTES;


    public WriteAheadLog(Path dataFile) throws IOException {
        if (Files.isDirectory(dataFile)) {
            dataFile = dataFile.resolve("wal.log");
        }
        this.dataFile = dataFile;
        this.channel = FileChannel.open(dataFile,
                StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        rebuildIndex(); // save the index too, to reduce startup speed
    }

    public Optional<byte[]> get(String key) {
        try {
            DiscIndexEntry entry = index.get(key);
            if (entry == null) return Optional.empty();

            if (entry.expireAt() != 0 && entry.expireAt() < System.currentTimeMillis()) {
                index.remove(key);
                return Optional.empty();
            }

            ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES);

            channel.read(header, entry.valuePos());
            header.flip();
            header.get(); // no need for opcode
            int keyLen = header.getInt();
            int valueLen = header.getInt();
            long ttl = header.getLong(); // TODO: check for TTL


            ByteBuffer valueBuf = ByteBuffer.allocate(valueLen);
            channel.read(valueBuf, entry.valuePos() + HEADER_BYTES + keyLen);
            return Optional.of(valueBuf.array());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public synchronized void put(String key, byte[] value, long ttlMs) {
        try {
            long expireAt = ttlMs > 0
                    ? System.currentTimeMillis() + ttlMs
                    : 0;
            long offset = appendEntry(CommandCodes.PUT, key, value, expireAt);
            index.put(key, new DiscIndexEntry(offset, expireAt));
        } catch (Exception e) {
            Storage.LOGGER.warning("put command failed" + e);
        }
    }

    public synchronized void remove(String key) {
        try {
            appendEntry(CommandCodes.DELETE, key, new byte[0], 0);
            index.remove(key);
        } catch (Exception e) {
            Storage.LOGGER.warning("remove command failed" + e);
        }
    }


    private long appendEntry(byte opcode, String key, byte[] value, long ttlMs) throws IOException {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        long offset = channel.size();

        ByteBuffer buf = ByteBuffer.allocate(HEADER_BYTES + keyBytes.length + value.length);
        //HEADER
        buf.put(opcode);
        buf.putInt(keyBytes.length);
        buf.putInt(value.length);
        buf.putLong(ttlMs);
        //DATA
        buf.put(keyBytes);
        buf.put(value);

        buf.flip();
        channel.write(buf, offset);
        return offset;
    }

    private void rebuildIndex() throws IOException {
        long pos = 0;
        long size = channel.size();
        while (pos < size) {
            long startPos = pos;
            ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES);
            channel.read(header, pos);
            header.flip();
            byte opcode = header.get();
            int keyLen = header.getInt();
            int valueLen = header.getInt();
            long ttl = header.getLong();

            ByteBuffer keyBuf = ByteBuffer.allocate(keyLen);
            channel.read(keyBuf, pos + HEADER_BYTES);
            String key = new String(keyBuf.array());
            switch (opcode) {
                case CommandCodes.PUT -> index.put(key, new DiscIndexEntry(startPos, ttl));
                case CommandCodes.DELETE -> index.remove(key);
            }
            pos = startPos + HEADER_BYTES + keyLen + valueLen;
        }
    }

    public synchronized void compact() throws IOException {
        Path tmp = dataFile.resolveSibling(dataFile.getFileName() + ".cmpt");
        HashMap<String, DiscIndexEntry>  newIndex = new HashMap<>();


        try (FileChannel newChannel = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            for (Map.Entry<String, DiscIndexEntry> e : index.entrySet()) {
                byte[] value = get(e.getKey()).orElse(null);
                if (value == null) continue;

                byte[] keyBytes = e.getKey().getBytes(StandardCharsets.UTF_8);
                long newOffset = newChannel.size();
                ByteBuffer buf = ByteBuffer.allocate(HEADER_BYTES + keyBytes.length + value.length);
                buf
                    .put(CommandCodes.PUT)
                    .putInt(keyBytes.length)
                    .putInt(value.length)
                    .putLong(e.getValue().expireAt())
                    .put(keyBytes)
                    .put(value);

                buf.flip();
                newChannel.write(buf, newOffset);
                newIndex.put(e.getKey(), new DiscIndexEntry(newOffset, e.getValue().expireAt()));
            }
        }

        channel.close();
        Files.move(tmp, dataFile, StandardCopyOption.REPLACE_EXISTING);
        channel = FileChannel.open(dataFile, StandardOpenOption.READ, StandardOpenOption.WRITE);
        index.clear();
        index.putAll(newIndex);
    }

}

package org.chivqsss.disc;

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
        this.dataFile = dataFile;
        this.channel = FileChannel.open(dataFile,
                StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        rebuildIndex(); // save the index too, to reduce startup speed
    }

    public Optional<byte[]> get(String key) {
        try {
            DiscIndexEntry entry = index.get(key);
            if (entry == null) return Optional.empty();

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

    public synchronized void put(String key, byte[] value, long ttl) {
        try {
            long offset = appendEntry(CommandCodes.PUT, key, value, ttl);
            index.put(key, new DiscIndexEntry(offset, ttl));
        } catch (Exception e) {

        }
    }


    private long appendEntry(byte opcode, String key, byte[] value, long ttl) throws IOException {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        long offset = channel.size();

        ByteBuffer buf = ByteBuffer.allocate(HEADER_BYTES + keyBytes.length + value.length);
        //HEADER
        buf.put(opcode);
        buf.putInt(keyBytes.length);
        buf.putInt(value.length);
        buf.putLong(ttl);
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
                    .putLong(e.getValue().ttl())
                    .put(keyBytes)
                    .put(value);

                buf.flip();
                newChannel.write(buf, newOffset);
                newIndex.put(e.getKey(), new DiscIndexEntry(newOffset, e.getValue().ttl()));
            }
        }

        channel.close();
        Files.move(tmp, dataFile, StandardCopyOption.REPLACE_EXISTING);
        channel = FileChannel.open(dataFile, StandardOpenOption.READ, StandardOpenOption.WRITE);
        index.clear();
        index.putAll(newIndex);
    }

}

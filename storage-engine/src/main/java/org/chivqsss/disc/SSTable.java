package org.chivqsss.disc;

import org.chivqsss.commands.CommandCodes;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;

public class SSTable {
    private static final int HEADER_BYTES = Integer.BYTES*2;
    private static final int BYTES_PER_STRING = 2;
    private static final int BITS_PER_STRING = BYTES_PER_STRING * 8;

    public static void writeToDisk(SortedMap<String, byte[]> dataToWrite, Path filePath) {
        long currentOffset = 0;
        try (FileChannel newChannel = FileChannel.open(filePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            SSTableBitSetPrefix bitSetPrefix = new SSTableBitSetPrefix(dataToWrite.size(), BITS_PER_STRING);
            ByteBuffer buf_index = ByteBuffer.allocate(dataToWrite.size() * BYTES_PER_STRING + Integer.BYTES);
            currentOffset += ((long) dataToWrite.size() * BYTES_PER_STRING) + Integer.BYTES;

            for (Map.Entry<String, byte[]> entry : dataToWrite.entrySet()) {
                String key = entry.getKey();
                byte[] value = entry.getValue();
                bitSetPrefix.add(key);
                byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
                ByteBuffer buf = ByteBuffer.allocate(HEADER_BYTES + keyBytes.length + value.length);
                buf
                        .putInt(keyBytes.length)
                        .putInt(value.length)
                        .put(keyBytes)
                        .put(value);

                buf.flip();
                newChannel.write(buf, currentOffset);
                currentOffset += buf.capacity();
            }

            buf_index.putInt(dataToWrite.size() * BYTES_PER_STRING);
            buf_index.put(bitSetPrefix.toByteArray());
            buf_index.flip();
            newChannel.write(buf_index, 0);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Optional<byte[]> getFromDrive(String key) {
        // get file list
        // read prefixes in files
        // get String hash and check if key might present if files
        // if not return Optional.empty();
        // else read needed file until key and data is found

        return Optional.empty();
    }
}

package org.chivqsss.disc;

import org.chivqsss.commands.CommandCodes;
import org.chivqsss.utils.FileUtils;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

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
                while (buf.hasRemaining()) {
                    currentOffset += newChannel.write(buf, currentOffset);
                }
            }

            buf_index.putInt(dataToWrite.size() * BYTES_PER_STRING);
            buf_index.put(bitSetPrefix.toByteArray());

            buf_index.position(0);
            buf_index.limit(buf_index.capacity());

            long writePos = 0;
            while (buf_index.hasRemaining()) {
                writePos += newChannel.write(buf_index, writePos);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Optional<byte[]> getFromDrive(String key, Path dirPath) {
        File[] sstables = FileUtils.findFilesSorted(dirPath, "sst");

        for (int i = 0; i < sstables.length; i++) {
            File sstable = sstables[i];
            try (FileChannel channel = FileChannel.open(sstable.toPath(), StandardOpenOption.READ)) {
                ByteBuffer sizeBuffer = ByteBuffer.allocate(Integer.BYTES);
                channel.read(sizeBuffer);
                sizeBuffer.flip();
                int prefixBytesSize = sizeBuffer.getInt();

                ByteBuffer prefixBuffer = ByteBuffer.allocate(prefixBytesSize);
                channel.read(prefixBuffer);
                prefixBuffer.flip();

                byte[] filterBytes = prefixBuffer.array();
                SSTableBitSetPrefix bitSet = new SSTableBitSetPrefix(filterBytes);

                if (!bitSet.mightContain(key)) {
                    continue;
                }

                byte[] searchKeyBytes = key.getBytes(StandardCharsets.UTF_8);
                // some loop
                // TODO: I think that binary search will be better for large files, but you need to write data offsets right after prefix, instead before each key
                while (channel.position() < channel.size()) {

                    ByteBuffer sizeDataBuffer = ByteBuffer.allocate(HEADER_BYTES);
                    channel.read(sizeDataBuffer);
                    sizeDataBuffer.flip();

                    int keySize = sizeDataBuffer.getInt();
                    int valueSize = sizeDataBuffer.getInt();

                    ByteBuffer keyBuffer = ByteBuffer.allocate(keySize);
                    channel.read(keyBuffer);
                    keyBuffer.flip();

                    byte[] dataKey = keyBuffer.array();
                    if (Arrays.equals(searchKeyBytes, dataKey)) {
                        ByteBuffer valueBuffer = ByteBuffer.allocate(valueSize);
                        channel.read(valueBuffer);
                        valueBuffer.flip();
                        return Optional.of(valueBuffer.array());
                    } else {
                        channel.position(channel.position() + valueSize);
                    }
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // get file list
        // read prefixes in files
        // get String hash and check if key might present if files
        // if not return Optional.empty();
        // else read needed file until key and data is found

        return Optional.empty();
    }
}

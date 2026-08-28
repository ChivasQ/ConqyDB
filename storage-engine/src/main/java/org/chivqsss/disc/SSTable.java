package org.chivqsss.disc;

import org.chivqsss.commands.CommandCodes;
import org.chivqsss.utils.FileUtils;

import java.io.*;
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
        int dataToWriteSize = dataToWrite.size();
        try (FileChannel newChannel = FileChannel.open(filePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {

            SSTableBitSetPrefix bitSetPrefix = new SSTableBitSetPrefix(dataToWriteSize, BITS_PER_STRING);

            ByteArrayOutputStream keysBlock = new ByteArrayOutputStream();
            DataOutputStream keysOut = new DataOutputStream(keysBlock);
            ByteArrayOutputStream valuesBlock = new ByteArrayOutputStream();
            long[] keyPositions = new long[dataToWriteSize];

            int idx = 0;
            long currentOffset = 0;
            for (Map.Entry<String, byte[]> entry : dataToWrite.entrySet()) {
                String key = entry.getKey();
                byte[] value = entry.getValue();

                byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
                bitSetPrefix.add(keyBytes);

                keyPositions[idx++] = keysOut.size();
                keysOut.writeInt(keyBytes.length);
                keysOut.write(keyBytes);
                keysOut.writeLong(currentOffset);
                keysOut.writeInt(value.length);

                valuesBlock.write(value);
                currentOffset += value.length;
            }

            long offset = 0;
            byte[] prefixBytes = bitSetPrefix.toByteArray();
            ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES + prefixBytes.length + Long.BYTES * 2);
            long positionsBlockSize = (long) dataToWriteSize * Long.BYTES;
            long keysBlockOffset = header.capacity() + positionsBlockSize;
            long valuesBlockOffset = keysBlockOffset + keysBlock.size();

            header
                    .putInt(prefixBytes.length)
                    .put(prefixBytes)
                    .putInt(dataToWriteSize)
                    .putLong(keysBlockOffset)
                    .putLong(valuesBlockOffset);

            header.flip();

            // writing header (prefix + keys offsets + value offsets)
            writeFully(newChannel, header, offset);
            offset += header.capacity();

            ByteBuffer posBuf = ByteBuffer.allocate((int) positionsBlockSize);
            for (long p : keyPositions) {
                posBuf.putLong(p);
            }
            posBuf.flip();


            writeFully(newChannel, posBuf, offset);
            offset += positionsBlockSize;


            // writing keys
            writeFully(newChannel, ByteBuffer.wrap(keysBlock.toByteArray()), offset);
            offset += keysBlock.size();

            // writing data
            writeFully(newChannel, ByteBuffer.wrap(valuesBlock.toByteArray()), offset);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        // put prefix
        // put len of list
        // put long[n] of key offsets
        // put keys with data offsets
        // put data
    }

    private static void writeFully(FileChannel ch, ByteBuffer buf, long position) throws IOException {
        long pos = position;
        while (buf.hasRemaining()) {
            pos += ch.write(buf, pos);
        }
    }

    private static void readFully(FileChannel ch, ByteBuffer buf, long position) throws IOException {
        long pos = position;
        while (buf.hasRemaining()) {
            int r = ch.read(buf, pos);
            if (r < 0) throw new EOFException("Unexpected end of file at " + pos);
            pos += r;
        }
    }

    public static Optional<byte[]> getFromDrive(String key, List<Path> sstables) {
        for (int i = 0; i < sstables.size(); i++) {
            Path sstable = sstables.get(i);
            try (FileChannel channel = FileChannel.open(sstable, StandardOpenOption.READ)) {
                ByteBuffer sizeBuffer = ByteBuffer.allocate(Integer.BYTES);
                readFully(channel, sizeBuffer, 0);
                sizeBuffer.flip();
                int prefixBytesSize = sizeBuffer.getInt();

                ByteBuffer prefixBuffer = ByteBuffer.allocate(prefixBytesSize);
                readFully(channel, prefixBuffer, Integer.BYTES);
                prefixBuffer.flip();

                byte[] filterBytes = prefixBuffer.array();

                long metaOffset = Integer.BYTES + prefixBytesSize;
                ByteBuffer meta = ByteBuffer.allocate(Integer.BYTES + Long.BYTES * 2);
                readFully(channel, meta, metaOffset);
                meta.flip();
                int n = meta.getInt();
                long keysBlockOffset = meta.getLong();
                long valuesBlockOffset = meta.getLong();

                SSTableBitSetPrefix bitSet = new SSTableBitSetPrefix(filterBytes, n, BITS_PER_STRING);

                byte[] searchKeyBytes = key.getBytes(StandardCharsets.UTF_8);
                if (!bitSet.mightContain(searchKeyBytes)) {
                    continue;
                }

                long positionsBlockOffset = metaOffset + meta.capacity();

                long L = 0, R = n - 1;
                while (L <= R) {
                    long mid = (L + R) / 2;

                    ByteBuffer posBuf = ByteBuffer.allocate(Long.BYTES);
                    readFully(channel, posBuf, positionsBlockOffset + mid * Long.BYTES);
                    posBuf.flip();
                    long keyRecordOffset = keysBlockOffset + posBuf.getLong();

                    ByteBuffer keyLenBuf = ByteBuffer.allocate(Integer.BYTES);
                    readFully(channel, keyLenBuf, keyRecordOffset);
                    keyLenBuf.flip();
                    int keyLen = keyLenBuf.getInt();

                    ByteBuffer keyBuf = ByteBuffer.allocate(keyLen);
                    readFully(channel, keyBuf, keyRecordOffset + Integer.BYTES);
                    keyBuf.flip();

                    int cmp = Arrays.compareUnsigned(keyBuf.array(), searchKeyBytes);
                    if (cmp == 0) {
                        ByteBuffer tail = ByteBuffer.allocate(Long.BYTES + Integer.BYTES);
                        readFully(channel, tail, keyRecordOffset + Integer.BYTES + keyLen);
                        tail.flip();
                        long valOffset = tail.getLong();
                        int valLen = tail.getInt();

                        ByteBuffer valueBuf = ByteBuffer.allocate(valLen);
                        readFully(channel, valueBuf, valuesBlockOffset + valOffset);
                        valueBuf.flip();
                        return Optional.of(valueBuf.array());
                    } else if (cmp < 0) {
                        L = mid + 1;
                    } else {
                        R = mid - 1;
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

package org.chivqsss.disc;

import java.util.Arrays;
import java.util.BitSet;

public class SSTableBitSetPrefix {
    private final BitSet bitSet;
    private final int size;

    public SSTableBitSetPrefix(int size, int bitsPerString) {
        this.bitSet = new BitSet(size * bitsPerString);
        this.size = size * bitsPerString;
    }

    public SSTableBitSetPrefix(int size) {
        this.bitSet = new BitSet(size * 16);
        this.size = size * 16;
    }

    public SSTableBitSetPrefix(byte[] set, int originalDataSize, int bitsPerString) {
        this.bitSet = BitSet.valueOf(set);
        this.size = originalDataSize * bitsPerString;
    }

    public void add(byte[] key) {
        int hashIndex = (Arrays.hashCode(key) & 0x7FFFFFFF) % size;
        bitSet.set(hashIndex);
    }

    public boolean mightContain(byte[] key) {
        if (size < 1) return false;
        int hashIndex = (Arrays.hashCode(key) & 0x7FFFFFFF) % size;
        return bitSet.get(hashIndex);
    }

    public byte[] toByteArray() {
        return bitSet.toByteArray();
    }

    public int size() {
        return bitSet.size();
    }
}

package org.chivqsss.disc;

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



    public void add(String key) {
        int hashIndex = Math.abs(key.hashCode() % size);
        bitSet.set(hashIndex);
    }

    public boolean mightContain(String key) {
        int hashIndex = Math.abs(key.hashCode() % size);
        return bitSet.get(hashIndex);
    }

    public byte[] toByteArray() {
        return bitSet.toByteArray();
    }

    public int size() {
        return bitSet.size();
    }
}

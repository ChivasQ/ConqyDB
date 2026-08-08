package org.chivqsss.disc;

public record DiscIndexEntry(long valuePos, long ttl) {
    public long expireAt() {
        return ttl;
    }
}

package org.chivqsss.commands;

import java.util.Optional;

public interface CommandTargetObject {
    void put(String key, byte[] value, long ttl);
    void delete(String key);
    Optional<byte[]> get(String key);
}

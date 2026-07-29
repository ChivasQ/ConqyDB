package org.chivqsss.commands;

public interface CommandTargetObject {
    void put(String key, byte[] value);
    void delete(String key);
    byte[] get(String key);
}

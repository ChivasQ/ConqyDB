package org.chivqsss.commands;

import org.chivqsss.ResponseEncoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public record PutCommand(String key, byte[] value, long ttl) implements ICommand {
    @Override
    public byte[] execute(CommandTargetObject storage) throws IOException {
        storage.put(key, value, ttl);
        return ResponseEncoder.encodeOk();
    }
}
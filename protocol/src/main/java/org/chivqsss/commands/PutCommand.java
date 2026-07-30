package org.chivqsss.commands;

import java.nio.charset.StandardCharsets;

public record PutCommand(String key, byte[] value, long ttl) implements ICommand {
    @Override
    public void execute(CommandTargetObject storage) {
        storage.put(key, value);
        IO.println("put " +  new String(value, StandardCharsets.UTF_8) + " bytes to " + key);
    }
}
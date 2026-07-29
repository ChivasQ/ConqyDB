package org.chivqsss.commands;

public record PutCommand(String key, byte[] value, long ttl) implements ICommand {
    @Override
    public void execute(CommandTargetObject storage) {
        storage.put(key, value);
    }
}
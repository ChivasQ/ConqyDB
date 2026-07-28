package org.chivqsss.commands;

public record PutCommand(String key, byte[] value, long ttl) implements ICommand {
}
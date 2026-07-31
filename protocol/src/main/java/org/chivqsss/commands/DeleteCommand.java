package org.chivqsss.commands;

import org.chivqsss.ResponseEncoder;

import java.io.IOException;

public record DeleteCommand(String key) implements ICommand {
    @Override
    public byte[] execute(CommandTargetObject storage) throws IOException {
        storage.delete(key);
        return ResponseEncoder.encodeOk();
    }
}

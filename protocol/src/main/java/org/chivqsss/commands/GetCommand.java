package org.chivqsss.commands;

import org.chivqsss.ResponseEncoder;

public record GetCommand(String key) implements ICommand {
    @Override
    public byte[] execute(CommandTargetObject storage) {
        return storage.get(key)
                .map(ResponseEncoder::encodeFound)
                .orElseGet(ResponseEncoder::encodeNotFound);
    }
}
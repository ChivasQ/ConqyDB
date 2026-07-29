package org.chivqsss.commands;

public record DeleteCommand(String key) implements ICommand {
    @Override
    public void execute(CommandTargetObject storage) {
        storage.delete(key);
    }
}

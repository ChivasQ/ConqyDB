package org.chivqsss.commands;

public sealed interface ICommand permits DeleteCommand, GetCommand, PutCommand {
    default void execute(CommandTargetObject storage) {}
}

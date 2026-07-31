package org.chivqsss.commands;

import java.io.IOException;

public sealed interface ICommand permits DeleteCommand, GetCommand, PutCommand {
    default byte[] execute(CommandTargetObject storage) throws IOException {
        return null;
    }
}

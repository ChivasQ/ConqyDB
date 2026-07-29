package org.chivqsss;

import org.chivqsss.commands.ICommand;

public class CommandWorker implements Runnable {
    private final CommandQueue queue;
    private final Storage storage;

    public CommandWorker(CommandQueue queue, Storage storage) {
        this.queue = queue;
        this.storage = storage;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                ICommand command = queue.dequeue();
                command.execute(storage);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

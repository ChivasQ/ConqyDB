package org.chivqsss;

import org.chivqsss.commands.ICommand;

import java.util.Arrays;

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
                CommandQueue.QueuedCommand qc = queue.dequeue();
                byte[] response = qc.command().execute(storage);
                qc.responseFuture().complete(response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
            }
        }
    }
}

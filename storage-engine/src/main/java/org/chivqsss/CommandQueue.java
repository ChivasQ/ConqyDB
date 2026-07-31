package org.chivqsss;


import org.chivqsss.commands.ICommand;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

public class CommandQueue {
    private final BlockingQueue<QueuedCommand> queue = new LinkedBlockingQueue<>();

    public CompletableFuture<byte[]> enqueue(ICommand command) {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        queue.add(new QueuedCommand(command, future));
        return future;
    }

    public QueuedCommand dequeue() throws InterruptedException {
        return queue.take();
    }

    public record QueuedCommand(ICommand command, CompletableFuture<byte[]> responseFuture) {
    }
}
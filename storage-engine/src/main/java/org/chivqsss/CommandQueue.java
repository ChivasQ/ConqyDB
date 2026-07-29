package org.chivqsss;


import org.chivqsss.commands.ICommand;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class CommandQueue {
    private final BlockingQueue<ICommand> queue = new LinkedBlockingQueue<>();

    public void enqueue(ICommand command) {
        IO.println("enqueue");
        queue.add(command);
    }

    public ICommand dequeue() throws InterruptedException {
        return queue.take();
    }
}

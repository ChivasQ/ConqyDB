package org.chivqsss;

import java.io.IOException;

public class Main {
    static void main(String[] args) throws IOException {
        CommandQueue queue = new CommandQueue();
        Storage storage = new Storage();

        CommandWorker worker = new CommandWorker(queue, storage);
        Thread workerThread = new Thread(worker);
        workerThread.start();

        Server server = new Server(8080, queue);
        IO.println("Server started!");
        server.start();
    }
}

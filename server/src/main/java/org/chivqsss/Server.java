package org.chivqsss;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class Server {
    private final int configuredPort;
    private volatile int boundPort;
    private final CommandQueue queue;
    private final ExecutorService clientPool = Executors.newVirtualThreadPerTaskExecutor();
    private final CountDownLatch bindLatch = new CountDownLatch(1);
    private final Logger LOGGER = Logger.getLogger("Server");

    public Server(int port, CommandQueue queue) {
        this.configuredPort = port;
        this.queue = queue;
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(configuredPort)) {
            this.boundPort = serverSocket.getLocalPort();
            bindLatch.countDown();

            while (!Thread.currentThread().isInterrupted()) {
                Socket client = serverSocket.accept();
                clientPool.submit(new ClientHandler(client, queue));
            }
        }
    }

    public int getPort() {
        return boundPort;
    }

    public void awaitBind() throws InterruptedException {
        bindLatch.await();
    }
}

package org.chivqsss;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;

public class Server {
    private final int configuredPort;
    private volatile int boundPort;
    private final CommandQueue queue;

    private final CountDownLatch bindLatch = new CountDownLatch(1);

    public Server(int port, CommandQueue queue) {
        this.configuredPort = port;
        this.queue = queue;
    }

    public void start() throws IOException { // single threaded for now
        try (ServerSocket serverSocket = new ServerSocket(configuredPort)) {
            this.boundPort = serverSocket.getLocalPort();
            bindLatch.countDown();
            while (!Thread.currentThread().isInterrupted()) {
                Socket client = serverSocket.accept();
                handleClient(client);
            }
        }
    }

    private void handleClient(Socket client) {
        try (client; InputStream in = client.getInputStream()) {
            ProtocolDecoder decoder = new ProtocolDecoder();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                decoder.feed(buffer, read);
                decoder.tryDecodeOne().ifPresent(queue::enqueue);
            }
        } catch (IOException _) {

        }
    }

    public int getPort() {
        return boundPort;
    }

    public void awaitBind() throws InterruptedException {
        bindLatch.await();
    }
}

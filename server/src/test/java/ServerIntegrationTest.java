import org.chivqsss.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ServerIntegrationTest {
    @Test
    void serverReceivesAndExecutesPutCommand() throws IOException, InterruptedException {
        Storage storage = new Storage();
        CommandQueue queue = new CommandQueue();
        CommandWorker worker = new CommandWorker(queue, storage);
        Thread workerThread = new Thread(worker);
        workerThread.setDaemon(true);
        workerThread.start();

        Server server = new Server(0, queue);
        Thread serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (IOException ignored) {}
        });
        serverThread.setDaemon(true);
        serverThread.start();

        server.awaitBind();

        try (Socket socket = new Socket("localhost", server.getPort())) {
            OutputStream out = socket.getOutputStream();
            byte[] encoded = ProtocolEncoder.encodePut("key", "value".getBytes(), 0);
            out.write(encoded);
        }

        Thread.sleep(200);
        assertEquals("key", new String(storage.get("value")));
    }
}
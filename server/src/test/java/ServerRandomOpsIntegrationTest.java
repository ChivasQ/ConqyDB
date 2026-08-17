import org.chivqsss.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ServerRandomOpsIntegrationTest {
    private static final int OPERATIONS = 5_000;
    private Random random;
    private Storage storage;
    private CommandQueue queue;
    private CommandWorker worker;
    private Thread workerThread;
    private Server server;
    private Thread serverThread;
    private CDBClient client;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException, InterruptedException {
        random = new Random();

        storage = new Storage(tempDir);
        queue = new CommandQueue();
        worker = new CommandWorker(queue, storage);
        workerThread = new Thread(worker);
        workerThread.setDaemon(true);
        workerThread.start();

        server = new Server(0, queue);
        serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (IOException ignored) {
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
        server.awaitBind();

        client = new CDBClient("localhost", server.getPort());
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
    }

    @Test
    void randomPutGetRemove() throws IOException {
        List<String> knownKeys = new ArrayList<>();
        Map<String, byte[]> alive = new HashMap<>();

        for (int i = 0; i < OPERATIONS; i++) {
            int op = knownKeys.isEmpty() ? 0 : random.nextInt(10);

            if (op < 5) {
                String key = (!knownKeys.isEmpty() && random.nextBoolean())
                        ? knownKeys.get(random.nextInt(knownKeys.size()))
                        : randomString(12);
                byte[] value = randomBytes(32);

                client.put(key, value, 0);
                if (!alive.containsKey(key)) knownKeys.add(key);
                alive.put(key, value);

            } else if (op < 8) {
                String key = random.nextDouble() < 0.7
                        ? knownKeys.get(random.nextInt(knownKeys.size()))
                        : randomString(12);

                Optional<byte[]> expected = Optional.ofNullable(alive.get(key));
                Optional<byte[]> actual = client.get(key);

                assertEquals(expected.isPresent(), actual.isPresent(),
                        "Presence mismatch for key: " + key);
                expected.ifPresent(exp -> assertArrayEquals(exp, actual.get(),
                        "Value mismatch for key: " + key));

            } else {
                String key = knownKeys.get(random.nextInt(knownKeys.size()));
                client.remove(key);
                alive.remove(key);
            }
        }

        for (String key : knownKeys) {
            Optional<byte[]> actual = client.get(key);
            if (alive.containsKey(key)) {
                assertTrue(actual.isPresent(), "Key should still be present: " + key);
                assertArrayEquals(alive.get(key), actual.get(), "Value mismatch for key: " + key);
            } else {
                assertTrue(actual.isEmpty(), "Removed key should stay absent: " + key);
            }
        }
    }

    private String randomString(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append((char)(random.nextInt(75) + 48));
        }
        return sb.toString();
    }

    private byte[] randomBytes(int len) {
        byte[] bytes = new byte[len];
        random.nextBytes(bytes);
        return bytes;
    }
}
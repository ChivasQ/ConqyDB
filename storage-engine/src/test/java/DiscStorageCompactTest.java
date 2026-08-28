import org.chivqsss.Engine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class DiscStorageCompactTest {
    Random random = new Random();

    @Test
    void randomPutAndDeleteTest(@TempDir Path tempDir) throws InterruptedException {
        // cacheMaxSize = 10_000
        // maxBytesBeforeFlush = 1_000_000
        Engine engine = new Engine(1000, 0.75f, 10000, 1_000_000, tempDir);

        Map<String, byte[]> alive = new HashMap<>();
        Set<String> deleted = new HashSet<>();

        for (int i = 0; i < 500_000; i++) {
            String s = getRandomString(16);
            byte[] b = getRandomBytes(32);

            alive.put(s, b);
            deleted.remove(s);
            engine.put(s, b, 0);

            int r = random.nextInt(10);
            if (r == 1) {
                alive.remove(s);
                deleted.add(s);
                engine.remove(s);
            }
        }

        Thread.sleep(2000);

        alive.forEach((k, v) -> {
            Optional<byte[]> stored = engine.get(k);
            assertTrue(stored.isPresent(), "Key should be present: " + k);
            assertArrayEquals(v, stored.get(), "Value mismatch for key: " + k);
        });

        deleted.forEach(k -> {
            Optional<byte[]> stored = engine.get(k);
            assertTrue(stored.isEmpty(), "Deleted key should stay absent: " + k);
        });
    }

    String getRandomString(int len) {
        StringBuilder str = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            str.append((char)(random.nextInt(84)+48));
        }
        return str.toString();
    }

    byte[] getRandomBytes(int len) {
        byte[] bytes = new byte[len];
        random.nextBytes(bytes);
        return bytes;
    }
}

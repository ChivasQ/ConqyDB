import org.chivqsss.disc.WriteAheadLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class DiscStorageCompactTest {
    Random random = new Random();

    @Test
    void randomPutAndDeleteTest(@TempDir Path tempDir) throws IOException {
        WriteAheadLog store = new WriteAheadLog(tempDir);

        Map<String, byte[]> alive = new HashMap<>();
        Set<String> deleted = new HashSet<>();
        for (int i = 0; i < 100_000; i++) {
            String s = getRandomString(16);
            byte[] b = getRandomBytes(32);
            alive.put(s, b);
            deleted.remove(s);
            store.put(s, b, 0);


            int r = random.nextInt(10);
            if (r == 1) {
                alive.remove(s);
                deleted.add(s);
                store.remove(s);
            }
        }

        store.compact();

        alive.forEach((k, v) -> {
            Optional<byte[]> stored = store.get(k);
            assertTrue(stored.isPresent(), "Key should be present after compact: " + k);
            assertArrayEquals(v, stored.get(), "Value mismatch for key: " + k);
        });

        deleted.forEach(k ->
                assertTrue(store.get(k).isEmpty(), "Deleted key should stay absent after compact: " + k)
        );
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

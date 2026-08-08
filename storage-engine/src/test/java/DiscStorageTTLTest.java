import org.chivqsss.disc.WriteAheadLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DiscStorageTTLTest {
    @Test
    void valueIsReturnedBeforeTtlExpires(@TempDir Path tempDir) throws IOException {
        WriteAheadLog store = new WriteAheadLog(tempDir);

        store.put("session:1", "active".getBytes(StandardCharsets.UTF_8), 5000); // lives 5 sec

        Optional<byte[]> value = store.get("session:1");

        assertTrue(value.isPresent());
        assertEquals("active", new String(value.get(), StandardCharsets.UTF_8));
    }


    @Test
    void valueDisappearsAfterTtlExpires(@TempDir Path tempDir) throws IOException, InterruptedException {
        WriteAheadLog store = new WriteAheadLog(tempDir);

        store.put("session:2", "active".getBytes(StandardCharsets.UTF_8), 100); // живёт 100мс

        Thread.sleep(105);

        Optional<byte[]> value = store.get("session:2");

        assertTrue(value.isEmpty(), "Значение должно быть недоступно после истечения TTL");
    }

    @Test
    void ttlZeroNeverExpires(@TempDir Path tempDir) throws IOException, InterruptedException {
        WriteAheadLog store = new WriteAheadLog(tempDir);

        store.put("config:forever", "value".getBytes(StandardCharsets.UTF_8), 0);

        Thread.sleep(100);

        Optional<byte[]> value = store.get("config:forever");

        assertTrue(value.isPresent(), "Запись без TTL не должна протухать");
    }
}

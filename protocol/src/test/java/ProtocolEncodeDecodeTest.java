import org.chivqsss.ProtocolDecoder;
import org.chivqsss.ProtocolEncoder;
import org.chivqsss.commands.ICommand;
import org.chivqsss.commands.PutCommand;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProtocolEncodeDecodeTest {
    Random random = new Random();
    @Test
    void decodesMessageSplitAcrossArbitraryChunks() throws IOException {
        ProtocolDecoder decoder = new ProtocolDecoder();

        for (int i = 0; i < 1_000; i++) {
            String key = getRandomString(16);
            byte[] value = getRandomBytes(128);
            byte[] encoded = ProtocolEncoder.encodePut(key, value, 0);

            for (byte b : encoded) {
                decoder.feed(new byte[]{b}, 1);
            }

            Optional<ICommand> result = decoder.tryDecodeOne();
            assertTrue(result.isPresent());
            assertEquals(key, ((PutCommand) result.get()).key());
        }
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

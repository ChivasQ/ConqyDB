package org.chivqsss;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class Main {
    public static void main(String[] args) throws IOException {
        try (CDBClient client= new CDBClient("localhost", 8080)) {
            client.putString("hello", "world", 0);
            Optional<String> value = client.getString("hello");
            value.ifPresentOrElse(
                    v -> System.out.println("Success: " + v),
                    () -> System.out.println("not found")
            );
        }
    }
}

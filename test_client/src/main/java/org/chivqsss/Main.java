package org.chivqsss;

import org.chivqsss.response.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class Main {
    public static void main(String[] args) throws IOException {
        try (Socket socket = new Socket("localhost", 8080)) {
            socket.setTcpNoDelay(true);
            ResponseDecoder decoder = new ResponseDecoder();
            OutputStream out = socket.getOutputStream();
            byte[] readBuf = new byte[4096];
            byte[] encoded = ProtocolEncoder.encodePut("key221", "helloo".getBytes(), 0);
            out.write(encoded);
            out.flush();
            Response putResponse = readOneResponse(socket, decoder, readBuf);
            System.out.println("PUT response: " + putResponse);

            out.write(ProtocolEncoder.encodeGet("key221"));
            out.flush();
            Response getResponse = readOneResponse(socket, decoder, readBuf);
            printGetResult(getResponse);
        }
    }

    private static Response readOneResponse(Socket socket, ResponseDecoder decoder, byte[] readBuf) throws IOException {
        Optional<Response> response;
        while ((response = decoder.tryDecodeOne()).isEmpty()) {
            int n = socket.getInputStream().read(readBuf);
            if (n == -1) throw new IOException("Server closed connection");
            decoder.feed(readBuf, n);
        }
        return response.get();
    }

    private static void printGetResult(Response response) {
        switch (response) {
            case FoundResponse found -> System.out.println("GET result: " + new String(found.value(), StandardCharsets.UTF_8));
            case NotFoundResponse ignored -> System.out.println("GET result: key not found");
            case ErrorResponse err -> System.out.println("GET error: " + err.message());
            case OkResponse ignored -> System.out.println("Unexpected OK response for GET");
        }
    }
}

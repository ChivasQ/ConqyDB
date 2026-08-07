package org.chivqsss;

import org.chivqsss.response.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class CDBClient implements AutoCloseable {
    private final Socket socket;
    private final OutputStream out;
    private final ResponseDecoder decoder = new ResponseDecoder();
    private final byte[] readBuf = new byte[4096];

    public CDBClient(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.out = socket.getOutputStream();
    }

    public void put(String key, byte[] value, long ttlMs) throws IOException {
        out.write(ProtocolEncoder.encodePut(key, value, ttlMs));
        out.flush();
        Response response = readOneResponse();
        if (response instanceof ErrorResponse err) {
            throw new CBDClientException(err.message());
        }
    }

    public Optional<byte[]> get(String key) throws IOException {
        out.write(ProtocolEncoder.encodeGet(key));
        out.flush();
        Response response = readOneResponse();
        return getOptionalBytes(response);
    }

    public void putString(String key, String value, long ttlMs) throws IOException {
        out.write(ProtocolEncoder.encodePut(key, value.getBytes(StandardCharsets.UTF_8), ttlMs));
        out.flush();
        Response response = readOneResponse();
        if (response instanceof ErrorResponse err) {
            throw new CBDClientException(err.message());
        }
    }

    public Optional<String> getString(String key) throws IOException {
        out.write(ProtocolEncoder.encodeGet(key));
        out.flush();
        Response response = readOneResponse();
        Optional<byte[]> optionalBytes = getOptionalBytes(response);
        return optionalBytes.map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    public void putInt(String key, int value, long ttlMs) throws IOException {
        out.write(ProtocolEncoder.encodePut(key, intToBytes(value), ttlMs));
        out.flush();
        Response response = readOneResponse();
        if (response instanceof ErrorResponse err) {
            throw new CBDClientException(err.message());
        }
    }

    public Optional<Integer> getInt(String key) throws IOException {
        out.write(ProtocolEncoder.encodeGet(key));
        out.flush();
        Response response = readOneResponse();
        Optional<byte[]> optionalBytes = getOptionalBytes(response);
        return optionalBytes.map(CDBClient::bytesToInt);
    }


    private static Optional<byte[]> getOptionalBytes(Response response) {
        return switch (response) {
            case FoundResponse found -> Optional.of(found.value());
            case NotFoundResponse ignored -> Optional.empty();
            case ErrorResponse err -> throw new CBDClientException(err.message());
            case OkResponse ignored -> throw new IllegalStateException("Unexpected OK for GET");
        };
    }

    public static byte[] intToBytes(int value) {
        return new byte[] {
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value
        };
    }

    public static int bytesToInt(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getInt();
    }

    private Response readOneResponse() throws IOException {
        Optional<Response> response;
        while ((response = decoder.tryDecodeOne()).isEmpty()) {
            int n = socket.getInputStream().read(readBuf);
            if (n == -1) throw new IOException("Server closed connection");
            decoder.feed(readBuf, n);
        }
        return response.get();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}

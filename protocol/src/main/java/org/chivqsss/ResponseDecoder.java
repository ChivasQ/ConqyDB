package org.chivqsss;

import org.chivqsss.response.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

public class ResponseDecoder {
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public void feed(byte[] chunk, int len) {
        buffer.write(chunk, 0, len);
    }

    public Optional<Response> tryDecodeOne() throws IOException {
        byte[] data = buffer.toByteArray();
        if (data.length < Integer.BYTES) return Optional.empty();

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        int bodyLen = in.readInt();

        if (data.length < Integer.BYTES + bodyLen) return Optional.empty();

        byte status = in.readByte();
        Response response = decodeBody(status, in, bodyLen - 1);

        byte[] remaining = Arrays.copyOfRange(data, 4 + bodyLen, data.length);
        buffer.reset();
        buffer.write(remaining, 0, remaining.length);

        return Optional.of(response);
    }

    private Response decodeBody(byte status, DataInputStream in, int remainingLen) throws IOException {
        return switch (status) {
            case ResponseCodes.OK -> new OkResponse();
            case ResponseCodes.NOT_FOUND -> new NotFoundResponse();
            case ResponseCodes.FOUND -> {
                byte[] value = in.readNBytes(remainingLen);
                yield new FoundResponse(value);
            }
            case ResponseCodes.ERROR -> {
                byte[] msgBytes = in.readNBytes(remainingLen);
                yield new ErrorResponse(new String(msgBytes, java.nio.charset.StandardCharsets.UTF_8));
            }
            default -> throw new IOException("Unknown response status: " + status);
        };
    }
}

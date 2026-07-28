package org.chivqsss;

import org.chivqsss.commands.DeleteCommand;
import org.chivqsss.commands.ICommand;
import org.chivqsss.commands.GetCommand;
import org.chivqsss.commands.PutCommand;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

public class ProtocolDecoder {
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public void feed(byte[] chunk, int len) throws IOException {
        buffer.write(chunk, 0, len);
    }

    public Optional<ICommand> tryDecodeOne() throws IOException {
        byte[] data = buffer.toByteArray();
        if (data.length < Integer.BYTES) return Optional.empty();

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        int bodyLen = in.readInt();

        if (data.length < Integer.BYTES + bodyLen) return Optional.empty();

        byte opcode = in.readByte();
        ICommand cmd = decodeBody(opcode, in);

        byte[] remaining = Arrays.copyOfRange(data, 4 + bodyLen, data.length);
        buffer.reset();
        buffer.write(remaining, 0, remaining.length);

        return Optional.of(cmd);
    }

    private ICommand decodeBody(byte opcode, DataInputStream in) throws IOException {
        return switch (opcode) {
            case 1 -> {
                int keyLen = in.readUnsignedShort();
                byte[] key = in.readNBytes(keyLen);
                int valLen = in.readInt();
                byte[] value = in.readNBytes(valLen);
                long ttl = in.readLong();
                yield new PutCommand(new String(key, StandardCharsets.UTF_8), value, ttl);
            }
            case 0 -> {
                int keyLen = in.readUnsignedShort();
                byte[] key = in.readNBytes(keyLen);
                yield new GetCommand(new String(key, StandardCharsets.UTF_8));
            }
            case 2 -> {
                int keyLen = in.readUnsignedShort();
                byte[] key = in.readNBytes(keyLen);
                yield new DeleteCommand(new String(key, StandardCharsets.UTF_8));
            }
            default -> throw new IOException("Unknown opcode: " + opcode);
        };
    }
}

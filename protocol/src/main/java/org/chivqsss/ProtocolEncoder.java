package org.chivqsss;

import org.chivqsss.commands.CommandCodes;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ProtocolEncoder {
    public static byte[] encodePut(String key, byte[] value, long ttlMs) throws IOException {
        ByteArrayOutputStream bodyStream = new ByteArrayOutputStream();
        DataOutputStream body = new DataOutputStream(bodyStream);

        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);

        body.writeByte(CommandCodes.PUT);      // opcode 0 = GET, 0 = PUT, 2 = DELETE
        body.writeShort(keyBytes.length);      // key_len
        body.write(keyBytes);                  // key
        body.writeInt(value.length);           // value_len
        body.write(value);                     // value
        body.writeLong(ttlMs);                 // ttl

        byte[] bodyBytes = bodyStream.toByteArray();

        ByteArrayOutputStream fullStream = new ByteArrayOutputStream();
        DataOutputStream full = new DataOutputStream(fullStream);
        full.writeInt(bodyBytes.length);       // length-prefix
        full.write(bodyBytes);

        return fullStream.toByteArray();
    }

    public static byte[] encodeGet(String key) throws IOException {
        ByteArrayOutputStream bodyStream = new ByteArrayOutputStream();
        DataOutputStream body = new DataOutputStream(bodyStream);

        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);

        body.writeByte(CommandCodes.GET);      // opcode 0 = GET, 0 = PUT, 2 = DELETE
        body.writeShort(keyBytes.length);      // key_len
        body.write(keyBytes);                  // key

        byte[] bodyBytes = bodyStream.toByteArray();

        ByteArrayOutputStream fullStream = new ByteArrayOutputStream();
        DataOutputStream full = new DataOutputStream(fullStream);
        full.writeInt(bodyBytes.length);       // length-prefix
        full.write(bodyBytes);

        return fullStream.toByteArray();
    }
}

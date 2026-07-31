package org.chivqsss;

import org.chivqsss.response.ResponseCodes;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

public class ResponseEncoder {
    public static byte[] encodeFound(byte[] value) {
        try {
            ByteArrayOutputStream bodyStream = new ByteArrayOutputStream();
            DataOutputStream body = new DataOutputStream(bodyStream);
            body.writeByte(ResponseCodes.FOUND);
            body.write(value);
            return wrapWithLength(bodyStream.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] encodeNotFound() {
        try {
            ByteArrayOutputStream bodyStream = new ByteArrayOutputStream();
            DataOutputStream body = new DataOutputStream(bodyStream);
            body.writeByte(ResponseCodes.NOT_FOUND);
            return wrapWithLength(bodyStream.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] encodeOk() {
        try {
            ByteArrayOutputStream bodyStream = new ByteArrayOutputStream();
            DataOutputStream body = new DataOutputStream(bodyStream);
            body.writeByte(ResponseCodes.OK);
            return wrapWithLength(bodyStream.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] encodeError() {
        try {
            ByteArrayOutputStream bodyStream = new ByteArrayOutputStream();
            DataOutputStream body = new DataOutputStream(bodyStream);
            body.writeByte(ResponseCodes.ERROR);
            return wrapWithLength(bodyStream.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] wrapWithLength(byte[] bodyBytes) throws IOException {
        ByteArrayOutputStream fullStream = new ByteArrayOutputStream();
        DataOutputStream full = new DataOutputStream(fullStream);
        full.writeInt(bodyBytes.length);
        full.write(bodyBytes);
        return fullStream.toByteArray();
    }
}

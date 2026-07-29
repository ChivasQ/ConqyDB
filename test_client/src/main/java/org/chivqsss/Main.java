package org.chivqsss;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

public class Main {
    public static void main(String[] args) throws IOException {
        try (Socket socket = new Socket("localhost", 8080)) {
            socket.setTcpNoDelay(true);
            OutputStream out = socket.getOutputStream();

            byte[] encoded = ProtocolEncoder.encodePut("key222", "valueeee222".getBytes(), 0);
            out.write(encoded);
            out.flush();

            System.out.println("Sent " + encoded.length + " bytes");
        }
    }
}

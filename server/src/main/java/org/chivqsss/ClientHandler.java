package org.chivqsss;

import org.chivqsss.commands.ICommand;

import java.io.IOException;
import java.net.Socket;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final CommandQueue commandHandler;

    public ClientHandler(Socket socket, CommandQueue commandHandler) {
        this.socket = socket;
        this.commandHandler = commandHandler;
    }

    @Override
    public void run() {
        try (socket) {
            ProtocolDecoder decoder = new ProtocolDecoder();
            byte[] readBuf = new byte[4096];
            int n;

            while ((n = socket.getInputStream().read(readBuf)) != -1) {
                decoder.feed(readBuf, n);

                Optional<ICommand> cmd;
                while ((cmd = decoder.tryDecodeOne()).isPresent()) {
                    CompletableFuture<byte[]> future = commandHandler.enqueue(cmd.get());
                    byte[] response = future.get();
                    socket.getOutputStream().write(response);
                    socket.getOutputStream().flush();
                }
            }
        } catch (IOException e) {
            System.err.println("Client disconnected: " + e.getMessage());
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
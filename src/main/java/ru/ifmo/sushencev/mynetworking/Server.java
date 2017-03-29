package ru.ifmo.sushencev.mynetworking;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Created by Jackson on 21.11.2016.
 */
public final class Server extends Thread {
    private ClientProcessorBuilder clientProcessorBuilder;
    private ServerSocketChannel serverSocketChannel;
    private Selector acceptSelector;
    private int port;

    public Server(int port, ClientProcessorBuilder clientProcessorBuilder) {
        this.clientProcessorBuilder = clientProcessorBuilder;
        this.port = port;

        System.out.printf("Server started on port %d\n", port);
    }

    private void setUpServer() throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        acceptSelector = Selector.open();

        try {
            serverSocketChannel.socket().bind(new InetSocketAddress(port));
            serverSocketChannel.socket().setReuseAddress(true);
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(acceptSelector, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
            serverSocketChannel.close();
            acceptSelector.close();
            throw e;
        }
    }

    @Deprecated
    public void run() {
        try {
            setUpServer();
        } catch (IOException e) {
            throw new MyNetworkException(e);
        }
        try {
            while (true) {
                int keysCount = acceptSelector.select();
                if (interrupted()) break;
                assert keysCount > 0;

                for (SelectionKey key : acceptSelector.selectedKeys()) {
                    assert key.isAcceptable();
                    ClientProcessor cp = clientProcessorBuilder.build(serverSocketChannel.accept());
                    if (connectionListener != null) connectionListener.accept(cp);
                    cp.start();
                }
                acceptSelector.selectedKeys().clear();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Consumer<ClientProcessor> connectionListener;

    public void setConnectionListener(Consumer<ClientProcessor> onConnected) {
        this.connectionListener = onConnected;
    }
}
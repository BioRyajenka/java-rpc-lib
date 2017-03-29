package ru.ifmo.sushencev.mynetworking;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

/**
 * Created by Jackson on 21.11.2016.
 */
public class ClientProcessor extends AbstractNetworkOperator {
    public ClientProcessor(SocketChannel channel) throws IOException {
        this.channel = channel;
        channel.configureBlocking(false);

        selector = Selector.open();

        channel.register(selector, SelectionKey.OP_READ, channel.socket().getPort());

        System.out.println(String.format("Registered client on port %d", channel.socket().getPort()));
        LOG_TAG = "CLIENT " + channel.socket().getPort();
    }

    @Deprecated
    public void run() {
        try {
            whil:
            while (true) {
                System.out.println("Before select");
                int keysCount = selector.select();
                if (disconnecting) {
                    System.out.println("Interrupted");
                    break;
                }

                if (keysCount == 0) {
                    System.out.println("woke up");
                    //printKeyCount0ErrorMessage();
                }

                System.out.println("======================cycle======================");
                for (SelectionKey key : selector.selectedKeys()) {
                    System.out.println("===================Another key====");
                    if (key.isWritable()) {
                        System.out.println("writable");
                        processWrite();
                    }
                    if (key.isReadable()) {
                        System.out.println("readable");
                        try {
                            if (!processRead()) {
                                System.out.println("Client closed connection");
                                break whil;
                            }
                        } catch (IOException e) { // just for not to clutter up output
                            System.out.println(e.toString());
                            break whil;
                        }
                    }
                }
                System.out.println("=================================================");
                selector.selectedKeys().clear();
            }
        } catch (IOException | MalformedMessageException e) {
            e.printStackTrace();
        }

        try {
            System.out.println("Closing channel");
            channel.close();
            selector.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        onConnectionClosed();
    }

    protected void onConnectionClosed() {
    }

    protected boolean disconnecting;

    public void disconnect() {
        disconnecting = true;
        selector.wakeup();
    }

    public boolean isConnected() {
        return channel.isConnected();
    }
}
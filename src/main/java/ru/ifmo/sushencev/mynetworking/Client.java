package ru.ifmo.sushencev.mynetworking;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;

/**
 * В схеме клиент-сервер этот класс представляет из себя клиент.
 * Он умеет стучаться по определенному порту к определенному серверу.
 *
 * {@link NetworkConversationAssistant} - это либо {@link ProcessorAssistant}, либо {@link RequesterAssistant}
 * Каждый класс (и Client и {@link ClientProcessor}) может иметь любой из этих ассистентов
 */
public class Client extends AbstractNetworkOperator {
    protected boolean started = false;
    protected boolean disconnecting = false;
    private boolean terminating = false;
    private String serverUrl;
    private int port;

    public Client(String serverUrl, int port) {
        this.serverUrl = serverUrl;
        this.port = port;
        start();
    }

    //=================== start/stop server ===================

    private void establishConnection() throws IOException {
        System.out.println("Establishing connection");
        channel = SocketChannel.open();
        selector = Selector.open();

        try {
            channel.configureBlocking(false);
            channel.connect(new InetSocketAddress(serverUrl, port));
            channel.register(selector, SelectionKey.OP_CONNECT);
            System.out.println("Connection established");
        } catch (IOException e) {
            channel.close();
            selector.close();
            throw e;
        }
    }

    public synchronized void connect() throws IOException {
        establishConnection();

        started = true;
        notify();
    }

    public synchronized void disconnect() {
        System.out.println("Trying to disconnect. Server now is started (" + started + ")");
        if (disconnecting || !started) return;
        disconnecting = true;
        selector.wakeup();
    }

    public synchronized void terminate() {
        terminating = true;
        disconnect();
    }

    private synchronized void processConnect() throws IOException {
        System.out.println("processConnect()");
        if (channel.finishConnect()) {
            System.out.println("successfully connected");
            channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        }
    }

    //==== other IO stuff ====
    @Override
    protected void scheduleWrite(MessageTarget target, String message) throws ClosedChannelException {
        System.out.println("=====================scheduleWrite()");
        System.out.println("Selector: " + selector);
        if (selector.keys().stream().anyMatch(key -> (key.interestOps() & SelectionKey.OP_CONNECT) != 0)) {
            System.out.println("\tscheduleWrite() OP_CONNECT");
            scheduleWriteWithoutModifyingSelector(target, message);
        } else {
            System.out.println("\tscheduleWrite() with modifying selector");
            super.scheduleWrite(target, message);
        }
        System.out.println("scheduleWrite()=====================");
    }

    //=================== main logic ===================

    @Override
    public void run() {
        while (true) {
            // 1. waiting till connection
            if (!waitTillStarted()) break; // interrupted
            // 2. do main logic
            try {
                whil:
                while (true) {
                    if (disconnecting) break;
                    int keysCount = selector.select();
                    if (disconnecting) break;

                    if (keysCount == 0) {
                        System.out.println("woke up");
                        printKeyCount0ErrorMessage();
                        try {
                            Thread.sleep(1000); // just for debug
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    System.out.println("======================cycle======================");
                    for (SelectionKey key : selector.selectedKeys()) {
                        //System.out.println("===================Another key====");
                        if (key.isConnectable()) {
                            System.out.println("connectable");
                            processConnect();
                        }
                        if (key.isWritable()) {
                            System.out.println("writable");
                            processWrite();
                        }
                        if (key.isReadable()) {
                            System.out.println("readable");
                            if (!processRead()) {
                                System.out.println("Lost connection with server");
                                break whil;
                            }
                        }
                    }
                    selector.selectedKeys().clear();
                    System.out.println("=================================================");
                }
                System.out.println("Closing channel");
            } catch (IOException | MalformedMessageException e) {
                exceptionListener.accept(e);
            }

            System.out.println("Actually disconnecting");
            disconnecting = started = false;
            clearData();
            try {
                channel.close();
                selector.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (terminating) break;
        }
    }

    // ======================== sugar =========================

    private Consumer<Exception> exceptionListener = Throwable::printStackTrace;

    public void setExceptionListener(Consumer<Exception> listener) {
        exceptionListener = listener;
    }

    // =================== auxilary methods ===================

    private synchronized boolean waitTillStarted() {
        try {
            while (!started) wait();
        } catch (InterruptedException e) {
            System.out.println("Client was interrupted");
            return false;
        }
        return true;
    }
}
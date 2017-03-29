package ru.ifmo.sushencev.mynetworking;

import com.google.common.base.Joiner;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.stream.Collectors;

/**
 * Created by Jackson on 21.11.2016.
 */
public abstract class AbstractNetworkOperator extends Thread {
    protected static String LOG_TAG = "ANO.java";

    protected SocketChannel channel;
    protected Selector selector;

    private RequesterAssistant requesterAssistant;
    private ProcessorAssistant processorAssistant;

    public enum MessageTarget {
        REQUESTER('R'), PROCESSOR('P');

        MessageTarget(char prefix) {
            this.prefix = prefix;
        }

        char prefix;
    }

    protected AbstractNetworkOperator() {
        requesterAssistant = new RequesterAssistant(this);
        processorAssistant = new ProcessorAssistant(this);
    }

    protected void clearData() {
        writeQueue.clear();
        if (writeByteBuffer != null) writeByteBuffer.clear();
        readBuffer = null;
    }

    //==================== High level IO commands ====================

    protected void onMessageReceived(String message) throws ClosedChannelException, MalformedMessageException {
        System.out.printf("Received message %s\n", beautifyMessage(message));
        if (message.charAt(0) == MessageTarget.REQUESTER.prefix) {
            requesterAssistant.onMessageReceived(message.substring(1));
        } else {
            processorAssistant.onMessageReceived(message.substring(1));
        }
    }

    private LinkedList<String> writeQueue = new LinkedList<>();

    protected void scheduleWriteWithoutModifyingSelector(MessageTarget target, String message) {
        System.out.println("scheduling write without modifying selector");
        writeQueue.add(target.prefix + message);
    }

    private static String beautifyMessage(String message) {
        String logMessage = message.replace("\n", " ");
        if (logMessage.length() > 80) logMessage = logMessage.substring(0, 80) + "...";
        return String.format("'%s' (length %d)", logMessage, message.length());
    }


    protected void scheduleWrite(MessageTarget target, String message) throws ClosedChannelException {
        String newMessage = target.prefix + message; // just for debug

        System.out.printf("[%s]: scheduling write of %s\n", LOG_TAG, beautifyMessage(newMessage));
        if (!channel.isOpen()) throw new ClosedChannelException();
        System.out.printf("[%s]: writing queue: '%s'\n\n", LOG_TAG, Joiner.on("', '").join(writeQueue));
        boolean needReregister = writeQueue.isEmpty();
        scheduleWriteWithoutModifyingSelector(target, message);
        if (needReregister) {
            channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            selector.wakeup();
        }
    }

    //=================== Medium level IO commands ===================
    private static final int READ_BUFFER_SIZE = 1024;
    private ByteBuffer readBuffer;

    /**
     * @return false if need to close connection and true otherwise
     */
    protected boolean processRead() throws IOException, MalformedMessageException {
        while (true) {
            if (readBuffer == null) {
                ByteBuffer localReadBuffer = ByteBuffer.allocate(READ_BUFFER_SIZE);
                int read = channel.read(localReadBuffer);
                if (read < 0) return false;
                if (read == 0) break;
                if (read < 4) {
                    // TODO: the situation can take place when int will not be read at once
                    // just break the connection
                    return false;
                }
                localReadBuffer.flip();
                int currentMessageReadLength = localReadBuffer.getInt();
                byte[] rest = Arrays.copyOfRange(localReadBuffer.array(), localReadBuffer
                        .position(), localReadBuffer.limit());
                readBuffer = ByteBuffer.allocate(currentMessageReadLength).put(rest);
            } else {
                int read = channel.read(readBuffer);
                if (read < 0) return false;
                if (read == 0) break;
            }
            if (!readBuffer.hasRemaining()) {
                String message = new String(readBuffer.array(), "UTF-8");
                readBuffer = null;
                onMessageReceived(message);
            }
        }
        return true;
    }

    protected void processWrite() throws IOException {
        if (writeQueue.isEmpty()) {
            channel.register(selector, SelectionKey.OP_READ);
            return;
        }
        System.out.println("processWrite(): " + beautifyMessage(writeQueue.getFirst()));
        if (sendSubString(writeQueue.getFirst())) {
            writeQueue.removeFirst(); // sent
        }
    }

    //==================== Low level IO commands ====================
    private ByteBuffer writeByteBuffer;

    /**
     * @return true if all the message was successfuly sent
     */
    private boolean sendSubString(String string) throws IOException {
        if (writeByteBuffer == null) {
            byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
            writeByteBuffer = ByteBuffer.allocate(bytes.length);
            writeByteBuffer.putInt(writeByteBuffer.limit());
            writeByteBuffer.flip();
            channel.write(writeByteBuffer); // TODO: actually, we may write nothing here. but who cares
            writeByteBuffer.clear();
            System.out.printf("Sending bytes len %d, buffer remainings %d\n", bytes.length, writeByteBuffer.remaining());
            writeByteBuffer.put(bytes);
            writeByteBuffer.flip();
        }

        System.out.printf("Write byte buffer limit is %d\n", writeByteBuffer.limit());
        channel.write(writeByteBuffer);
        if (!writeByteBuffer.hasRemaining()) {
            writeByteBuffer = null;
            return true;
        }
        return false;
    }

    //======================= aux methods =======================
    protected void printKeyCount0ErrorMessage() {
        System.out.println("Keys count is 0 in ServerConnectionManager.java");
        System.out.printf("[%s]\n", Joiner.on(", ")
                .join(selector.selectedKeys().stream().map(SelectionKey::readyOps)
                        .collect(Collectors.toList())));
        System.out.printf("[%s]\n", Joiner.on(", ")
                .join(selector.selectedKeys().stream().map(SelectionKey::interestOps)
                        .collect(Collectors.toList())));
        System.out.printf("[%s]\n", Joiner.on(", ")
                .join(selector.keys().stream().map(SelectionKey::readyOps).collect(Collectors.toList())));
        System.out.printf("[%s]\n", Joiner.on(", ")
                .join(selector.keys().stream().map(SelectionKey::interestOps).collect(Collectors.toList())));
    }

    //========================= getters =========================

    public RequesterAssistant getRequesterAssistant() {
        return requesterAssistant;
    }

    public ProcessorAssistant getProcessorAssistant() {
        return processorAssistant;
    }
}
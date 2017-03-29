package ru.ifmo.sushencev.mynetworking;

import com.sun.org.apache.xml.internal.security.exceptions.Base64DecodingException;
import com.sun.org.apache.xml.internal.security.utils.Base64;

import java.io.*;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Created by Jackson on 22.11.2016.
 */
public abstract class NetworkConversationAssistant {
    protected static final String COMMAND_SEPARATOR = "|";
    protected static final String STATUS_OK = "ok";
    protected static final String STATUS_FAIL = "fail";

    protected AbstractNetworkOperator networkOperator;
    private AbstractNetworkOperator.MessageTarget messageTarget;

    protected NetworkConversationAssistant(AbstractNetworkOperator networkOperator, AbstractNetworkOperator.MessageTarget messageTarget) {
        this.networkOperator = networkOperator;
        this.messageTarget = messageTarget;
    }

    protected void onMessageReceived(String message) throws MalformedMessageException, ClosedChannelException {
        String ss[] = message.split(Pattern.quote(COMMAND_SEPARATOR), 2);
        if (ss.length != 2 || ss[1].length() == 0) throw new MalformedMessageException(message);
        // actually, we don't need to check for (ss[0].length() == 0) here
        int id;
        try {
            id = Integer.parseInt(ss[0]);
        } catch (NumberFormatException e) {
            System.out.println("Malformed message received");
            throw new MalformedMessageException(message);
        }
        String body = ss[1];

        System.out.println("Before calling onMessageReceived(id, body)");
        onMessageReceived(id, body);
    }

    protected abstract void onMessageReceived(int id, String body) throws MalformedMessageException, ClosedChannelException;

    protected void scheduleMessageWrite(int id, String body) throws ClosedChannelException {
        networkOperator.scheduleWrite(messageTarget, id + COMMAND_SEPARATOR + body);
    }

    public static String serializeArguments(Object... args) {
        List<Object> list = Arrays.asList(args);
        String res = null;
        try (ByteArrayOutputStream bo = new ByteArrayOutputStream();
             ObjectOutputStream so = new ObjectOutputStream(bo)) {
            so.writeObject(list);
            so.flush();
            res = Base64.encode(bo.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return res;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> deserializeArguments(String serialized) throws Base64DecodingException, IOException, ClassNotFoundException {
        byte b[] = Base64.decode(serialized.getBytes(StandardCharsets.UTF_8));
        ByteArrayInputStream bi = new ByteArrayInputStream(b);
        ObjectInputStream si = new ObjectInputStream(bi);
        return (List<Object>) si.readObject();
    }
}
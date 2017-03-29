package ru.ifmo.sushencev.mynetworking;

import java.nio.channels.ClosedChannelException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Created by Jackson on 22.11.2016.
 */
public final class RequesterAssistant extends NetworkConversationAssistant {
    private int freeQueryId = 1;

    protected RequesterAssistant(AbstractNetworkOperator networkOperator) {
        super(networkOperator, AbstractNetworkOperator.MessageTarget.PROCESSOR);
    }

    private int getNextFreeId() {
        int res = freeQueryId++;
        if (freeQueryId == 10000) freeQueryId = 1;
        return res;
    }

    private Map<Integer, BiConsumer<String, String>> answerConsumers = new HashMap<>();

    public void sendQuery(String query, Consumer<String> onFail, Consumer<String> onSuccess) throws ClosedChannelException {
        System.out.println("RequesterAssistant.java:sendQuery1()");
        sendQuery(query, "", onFail, onSuccess);
    }

    public void sendQuery(String query, String argument, Consumer<String> onFail, Consumer<String> onSuccess) throws ClosedChannelException {
        System.out.println("RequesterAssistant.java:sendQuery2()");
        int id = getNextFreeId();
        scheduleMessageWrite(id, query + COMMAND_SEPARATOR + argument);
        answerConsumers.put(id, (status, result) -> {
            if (status.equals(STATUS_OK)) {
                onSuccess.accept(result);
            } else {
                onFail.accept(result);
            }
        });
    }

    @Override
    protected void onMessageReceived(int id, String body) throws MalformedMessageException {
        String[] ss = body.split(Pattern.quote(COMMAND_SEPARATOR), 2);
        if (ss.length != 2 || ss[0].length() == 0 || ss[1].length() == 0 ||
                !(ss[0].equals(STATUS_OK) || ss[0].equals(STATUS_FAIL)))
            throw new MalformedMessageException(body);
        String status = ss[0];
        String result = ss[1];
        answerConsumers.get(id).accept(status, result);
        answerConsumers.remove(id);
    }
}
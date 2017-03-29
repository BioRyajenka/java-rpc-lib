package ru.ifmo.sushencev.mynetworking;

import java.nio.channels.ClosedChannelException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Created by Jackson on 22.11.2016.
 */
public final class ProcessorAssistant extends NetworkConversationAssistant {
    protected ProcessorAssistant(AbstractNetworkOperator networkOperator) {
        super(networkOperator, AbstractNetworkOperator.MessageTarget.REQUESTER);
    }

    private Map<String, DefferedFunctionWithExecutionCheck> messageProcessors = new HashMap<>();

    public void addProcessor(String command, DefferedFunctionWithExecutionCheck processor) {
        messageProcessors.put(command, processor);
    }

    public void addProcessor(String command, FunctionWithExecutionCheck processor) {
        messageProcessors.put(command, (id, argument) -> processor.apply(argument));
    }

    public void addProcessor(String command, SupplierWithExecutionCheck processor) {
        messageProcessors.put(command, (id, argument) -> processor.get());
    }

    @Override
    protected void onMessageReceived(int id, String body) throws MalformedMessageException, ClosedChannelException {
        System.out.printf("Client said (%d|%s)\n", id, body);

        String[] ss = body.split(Pattern.quote(COMMAND_SEPARATOR), 2);
        if (ss.length != 2 || ss[0].length() == 0) throw new MalformedMessageException(body);
        String command = ss[0];
        String argument = ss[1];

        if (messageProcessors.containsKey(command)) {
            try {
                String result = messageProcessors.get(command).apply(id, argument);
                if (result != null) { // null means deferred answer
                    scheduleMessageWrite(id, STATUS_OK + COMMAND_SEPARATOR + result);
                } else {
                    System.out.printf("message processor for %s returned null, so it is deferred answer\n", command);
                }
            } catch (UnsuccessfulRequestExecutionException e) {
                scheduleMessageWrite(id, STATUS_FAIL + COMMAND_SEPARATOR + e.getMessage());
            }
        } else {
            scheduleMessageWrite(id, STATUS_FAIL + COMMAND_SEPARATOR + "Unrecognized command");
        }
    }

    public void scheduleMessageWriteDeferred(int id, SupplierWithExecutionCheck processor) throws ClosedChannelException {
        try {
            String result = processor.get();
            scheduleMessageWrite(id, STATUS_OK + COMMAND_SEPARATOR + result);
        } catch (UnsuccessfulRequestExecutionException e) {
            scheduleMessageWrite(id, STATUS_FAIL + COMMAND_SEPARATOR + e.toString());
        }
    }

    public interface SupplierWithExecutionCheck {
        String get() throws UnsuccessfulRequestExecutionException;
    }

    public interface FunctionWithExecutionCheck {
        String apply(String string) throws UnsuccessfulRequestExecutionException;
    }

    public interface DefferedFunctionWithExecutionCheck {
        String apply(int id, String string) throws UnsuccessfulRequestExecutionException;
    }
}
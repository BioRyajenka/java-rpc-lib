package ru.ifmo.sushencev.mynetworking;

import java.io.IOException;
import java.nio.channels.SocketChannel;

/**
 * Created by Jackson on 21.11.2016.
 */
public interface ClientProcessorBuilder {
    public ClientProcessor build(SocketChannel channel) throws IOException;
}
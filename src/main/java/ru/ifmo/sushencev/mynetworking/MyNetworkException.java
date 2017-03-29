package ru.ifmo.sushencev.mynetworking;

/**
 * Created by Jackson on 21.11.2016.
 */
public class MyNetworkException extends RuntimeException {
    MyNetworkException(Exception e) {
        super(e);
    }

    @Override
    public String toString() {
        return "MyNetworkException: " + super.getCause();
    }
}

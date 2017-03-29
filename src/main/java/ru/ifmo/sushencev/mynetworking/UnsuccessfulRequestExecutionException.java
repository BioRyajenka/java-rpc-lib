package ru.ifmo.sushencev.mynetworking;

/**
 * Created by Jackson on 22.11.2016.
 */
public class UnsuccessfulRequestExecutionException extends Exception {
    public UnsuccessfulRequestExecutionException(String commentary) {
        super(commentary);
    }
}

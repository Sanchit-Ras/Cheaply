package com.cheaply.exception;

public class CheaplyException extends RuntimeException {
    public CheaplyException(String message) {
        super(message);
    }

    public CheaplyException(String message, Throwable cause) {
        super(message, cause);
    }
}

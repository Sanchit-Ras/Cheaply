package com.cheaply.exception;

public class ScraperUnavailableException extends CheaplyException {
    public ScraperUnavailableException(String message) {
        super(message);
    }

    public ScraperUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

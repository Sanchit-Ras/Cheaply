package com.cheaply.exception;

public class UserAlreadyExistsException extends CheaplyException {
    public UserAlreadyExistsException(String message) {
        super(message);
    }
}

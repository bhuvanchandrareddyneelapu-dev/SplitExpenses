package com.splitwisemoney.exception;

public class ResourceConflictException extends IllegalArgumentException {
    public ResourceConflictException(String message) {
        super(message);
    }
}

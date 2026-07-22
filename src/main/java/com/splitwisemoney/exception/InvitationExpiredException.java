package com.splitwisemoney.exception;

public class InvitationExpiredException extends IllegalArgumentException {
    public InvitationExpiredException(String message) {
        super(message);
    }
}

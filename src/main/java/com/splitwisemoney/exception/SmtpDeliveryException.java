package com.splitwisemoney.exception;

public class SmtpDeliveryException extends RuntimeException {
    public SmtpDeliveryException(String message) {
        super(message);
    }

    public SmtpDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}

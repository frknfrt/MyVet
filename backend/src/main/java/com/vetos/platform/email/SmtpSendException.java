package com.vetos.platform.email;

/** SmtpMailClient.send basarisiz oldugunda -- caller kendi domain exception'ina cevirir ya da loglayip yutar. */
public class SmtpSendException extends RuntimeException {
    public SmtpSendException(String message, Throwable cause) {
        super(message, cause);
    }
}

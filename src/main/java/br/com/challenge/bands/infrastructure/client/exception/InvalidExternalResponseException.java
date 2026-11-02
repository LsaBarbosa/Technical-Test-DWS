package br.com.challenge.bands.infrastructure.client.exception;

public class InvalidExternalResponseException extends RuntimeException {

    public InvalidExternalResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}

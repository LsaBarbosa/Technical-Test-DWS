package br.com.challenge.bands.web.exception;

public class InvalidRequestParameterException extends RuntimeException {

    public InvalidRequestParameterException(String message) {
        super(message);
    }
}

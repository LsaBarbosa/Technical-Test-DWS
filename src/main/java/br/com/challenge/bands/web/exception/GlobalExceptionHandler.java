package br.com.challenge.bands.web.exception;

import br.com.challenge.bands.application.service.BandNotFoundException;
import br.com.challenge.bands.infrastructure.client.exception.ExternalServiceException;
import br.com.challenge.bands.infrastructure.client.exception.InvalidExternalResponseException;
import br.com.challenge.bands.web.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BandNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(BandNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler({InvalidRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception, HttpServletRequest request) {
        String message = exception instanceof MethodArgumentTypeMismatchException mismatch
                ? "Invalid value for parameter '" + mismatch.getName() + "'"
                : exception.getMessage();
        return error(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ApiErrorResponse> handleExternalService(
            ExternalServiceException exception, HttpServletRequest request) {
        HttpStatus status = hasTimeoutCause(exception) ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE;
        String message = status == HttpStatus.GATEWAY_TIMEOUT
                ? "External service timed out"
                : "External service is unavailable";
        return error(status, message, request);
    }

    @ExceptionHandler(InvalidExternalResponseException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidExternalResponse(
            InvalidExternalResponseException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_GATEWAY, "External service returned an invalid response", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private boolean hasTimeoutCause(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException
                    || cause instanceof HttpTimeoutException
                    || cause instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String message, HttpServletRequest request) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}

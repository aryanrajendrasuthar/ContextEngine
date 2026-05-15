
package com.contextengine.ingestion.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final URI VALIDATION_TYPE = URI.create("https://contextengine.io/errors/validation");
    private static final URI INTERNAL_TYPE = URI.create("https://contextengine.io/errors/internal");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (a, b) -> a
                ));

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(VALIDATION_TYPE);
        problem.setTitle("Validation Failed");
        problem.setDetail("One or more fields failed validation");
        problem.setProperty("fieldErrors", fieldErrors);

        log.warn("Validation failure: {}", fieldErrors);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(Exception ex) {
        log.error("Unhandled exception in ingestion-service", ex);

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setType(INTERNAL_TYPE);
        problem.setTitle("Internal Server Error");
        problem.setDetail("An unexpected error occurred. The request has been logged.");
        return problem;
    }
}

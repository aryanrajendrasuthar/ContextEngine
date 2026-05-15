
package com.contextengine.query.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URI;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String BASE_TYPE = "https://contextengine.io/errors/";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Validation Failed");
        problem.setType(type("validation-failed"));
        return problem;
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ProblemDetail handleUpstreamError(WebClientResponseException ex) {
        log.error("Upstream service error: {} {}", ex.getStatusCode(), ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "An upstream service returned an error: " + ex.getStatusCode());
        problem.setTitle("Upstream Error");
        problem.setType(type("upstream-error"));
        return problem;
    }

    @ExceptionHandler(WebClientRequestException.class)
    public ProblemDetail handleUpstreamUnavailable(WebClientRequestException ex) {
        log.error("Upstream service unreachable: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "A required upstream service is unreachable");
        problem.setTitle("Service Unavailable");
        problem.setType(type("service-unavailable"));
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setTitle("Internal Server Error");
        problem.setType(type("internal-error"));
        return problem;
    }

    @org.springframework.lang.NonNull
    private static URI type(String suffix) {
        return Objects.requireNonNull(URI.create(BASE_TYPE + suffix));
    }
}

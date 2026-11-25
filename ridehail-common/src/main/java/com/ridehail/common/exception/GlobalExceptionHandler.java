package com.ridehail.common.exception;

import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.response.ApiResponseService;

import jakarta.validation.ConstraintViolationException;

/**
 * Central error formatting so every service returns the same envelope shape the legacy
 * joiValidator/controllers produced, instead of each controller hand-rolling try/catch.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ApiResponseService apiResponseService;

    public GlobalExceptionHandler(ApiResponseService apiResponseService) {
        this.apiResponseService = apiResponseService;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<?> handleApiException(ApiException e) {
        return apiResponseService.error(e.getStatusCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return apiResponseService.error(StatusCode.UNPROCESSABLE_ENTITY, message.isBlank() ? "Validation failed" : message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(cv -> cv.getPropertyPath() + " " + cv.getMessage())
                .collect(Collectors.joining(", "));
        return apiResponseService.error(StatusCode.UNPROCESSABLE_ENTITY, message.isBlank() ? "Validation failed" : message);
    }

    @ExceptionHandler({
            org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.servlet.NoHandlerFoundException.class })
    public ResponseEntity<?> handleNotFound(Exception e) {
        return apiResponseService.error(StatusCode.NOT_FOUND, "Not Found");
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> handleMethodNotAllowed(org.springframework.web.HttpRequestMethodNotSupportedException e) {
        return apiResponseService.error(405, "Method Not Allowed");
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleUnreadableBody(org.springframework.http.converter.HttpMessageNotReadableException e) {
        return apiResponseService.error(StatusCode.BAD_REQUEST, "Malformed request body");
    }

    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<?> handleMissingParam(org.springframework.web.bind.MissingServletRequestParameterException e) {
        return apiResponseService.error(StatusCode.BAD_REQUEST, e.getParameterName() + " is required");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return apiResponseService.error(StatusCode.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.");
    }
}

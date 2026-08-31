package com.neaz.travel.visa.presentation.controller;

import com.neaz.travel.visa.common.exception.ApplicantProfileNotFoundException;
import com.neaz.travel.visa.common.exception.DocumentNotFoundException;
import com.neaz.travel.visa.common.exception.InsufficientContextException;
import com.neaz.travel.visa.common.exception.VisaRagException;
import com.neaz.travel.visa.common.model.ApiResponse;
import com.neaz.travel.visa.presentation.dto.VisaQueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Translates exceptions raised anywhere in the request-handling path into the
 * {@link ApiResponse} envelope every endpoint otherwise returns on success, so
 * clients never see a bare stack trace or framework-default error body.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INSUFFICIENT_CONTEXT_MESSAGE = "I couldn't find sufficient official information.";

    /**
     * Confidence below the configured threshold is a valid RAG outcome, not a
     * failure: the client receives the same {@code VisaQueryResponse} shape it
     * would on a successful query, with the fixed fallback answer text and no
     * citations.
     */
    @ExceptionHandler(InsufficientContextException.class)
    public ResponseEntity<ApiResponse<VisaQueryResponse>> handleInsufficientContext(InsufficientContextException ex) {
        VisaQueryResponse fallback = new VisaQueryResponse(INSUFFICIENT_CONTEXT_MESSAGE, List.of(), ex.getConfidenceScore());
        return ResponseEntity.ok(ApiResponse.success(fallback));
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleDocumentNotFound(DocumentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(ApplicantProfileNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleApplicantProfileNotFound(ApplicantProfileNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Raised for malformed applicant-profile input (an unknown field path, or
     * a value that doesn't match the field's declared type) — always caused
     * by bad client input, so this is a 400, not the catch-all's 500.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Catch-all for any {@link VisaRagException} subtype without a more
     * specific handler above. These are raised deliberately by domain/application
     * code, so the message is safe to expose to the client.
     */
    @ExceptionHandler(VisaRagException.class)
    public ResponseEntity<ApiResponse<?>> handleVisaRagException(VisaRagException ex) {
        log.warn("Domain exception: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(message));
    }

    /**
     * Last-resort handler for anything unexpected. The client gets a generic
     * message only — exception details are logged server-side, never exposed.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleUnexpectedException(Exception ex) {
        log.error("Unexpected error handling request", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred. Please try again later."));
    }
}

package com.neaz.travel.visa.common.exception;

/**
 * Base unchecked exception for the Visa RAG Service.
 * All domain- and application-specific exceptions extend this type so that
 * they can be handled uniformly by a single exception-handling boundary.
 */
public class VisaRagException extends RuntimeException {

    public VisaRagException(String message) {
        super(message);
    }

    public VisaRagException(String message, Throwable cause) {
        super(message, cause);
    }
}
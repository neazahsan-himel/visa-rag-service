package com.neaz.travel.visa.common.exception;

/**
 * Thrown when the retrieved context's confidence score falls below the
 * configured threshold, indicating the available official documents do not
 * sufficiently answer the query.
 */
public class InsufficientContextException extends VisaRagException {

    private final double confidenceScore;

    public InsufficientContextException(double confidenceScore) {
        super("Insufficient confidence in retrieved context: " + confidenceScore);
        this.confidenceScore = confidenceScore;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }
}
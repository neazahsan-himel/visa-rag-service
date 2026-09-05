package com.neaz.travel.visa.domain.model;

/**
 * The kind of document an applicant has uploaded. Determines which
 * quality-check, OCR, and field-extraction path {@code DocumentIntelligenceService}
 * runs for a given {@link ApplicantDocument}.
 *
 * <p>Only {@code PASSPORT} is supported so far. NID, bank statement, and photo
 * pipelines from the Phase 2 proposal are not yet implemented and are
 * deliberately not declared here until their extraction logic actually exists.
 */
public enum DocumentType {

    /** A passport bio-data page, expected to contain a machine-readable zone (MRZ). */
    PASSPORT
}

package com.neaz.travel.visa.application.result;

import com.neaz.travel.visa.domain.model.Citation;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Outcome of the "what documents does this application need" use case,
 * returned by {@code ChecklistService} to {@code ApplicationController}. The
 * checklist text itself is a grounded RAG answer (same confidence/citation
 * guarantees as a normal visa query) — this is not a separately maintained
 * structured document list.
 *
 * @param applicationId   the application this checklist was generated for
 * @param checklistText   the generated answer describing required documents
 * @param citations       citations backing the checklist text
 * @param confidenceScore score in {@code [0.0, 1.0]} computed by {@code ConfidenceScorer}
 */
public record ChecklistResult(UUID applicationId, String checklistText, List<Citation> citations, double confidenceScore) {

    public ChecklistResult {
        Objects.requireNonNull(applicationId, "applicationId must not be null");
        if (checklistText == null || checklistText.isBlank()) {
            throw new IllegalArgumentException("checklistText must not be blank");
        }
        citations = List.copyOf(Objects.requireNonNull(citations, "citations must not be null"));
        if (confidenceScore < 0.0 || confidenceScore > 1.0) {
            throw new IllegalArgumentException("confidenceScore must be within [0.0, 1.0]");
        }
    }
}

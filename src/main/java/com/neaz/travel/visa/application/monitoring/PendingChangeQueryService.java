package com.neaz.travel.visa.application.monitoring;

import com.neaz.travel.visa.application.result.PendingChangeResult;
import com.neaz.travel.visa.domain.model.Document;
import com.neaz.travel.visa.domain.model.SourceCheckLog;
import com.neaz.travel.visa.domain.repository.DocumentRepository;
import com.neaz.travel.visa.domain.repository.SourceCheckLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Lists unreviewed {@code CHANGED} source-check results for the curator
 * review workflow. Separate from {@link SourceChangeMonitor}, which runs
 * checks rather than querying their results.
 */
@Service
public class PendingChangeQueryService {

    private final SourceCheckLogRepository sourceCheckLogRepository;
    private final DocumentRepository documentRepository;

    public PendingChangeQueryService(SourceCheckLogRepository sourceCheckLogRepository,
                                      DocumentRepository documentRepository) {
        this.sourceCheckLogRepository = sourceCheckLogRepository;
        this.documentRepository = documentRepository;
    }

    public List<PendingChangeResult> findPendingChanges() {
        return sourceCheckLogRepository.findPendingReview().stream()
                .map(this::toResult)
                .toList();
    }

    private PendingChangeResult toResult(SourceCheckLog checkLog) {
        Document document = documentRepository.findById(checkLog.documentId())
                .orElseThrow(() -> new IllegalStateException(
                        "Referenced document id %s no longer exists".formatted(checkLog.documentId())));

        return new PendingChangeResult(
                checkLog.id(),
                document.getId(),
                document.getTitle(),
                document.getMetadata().sourceUrl(),
                checkLog.checkedAt(),
                checkLog.detectedHash());
    }
}

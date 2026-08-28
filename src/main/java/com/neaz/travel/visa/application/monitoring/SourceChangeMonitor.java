package com.neaz.travel.visa.application.monitoring;

import com.neaz.travel.visa.application.port.DocumentParsingPort;
import com.neaz.travel.visa.common.exception.VisaRagException;
import com.neaz.travel.visa.common.util.Sha256Hasher;
import com.neaz.travel.visa.domain.model.Document;
import com.neaz.travel.visa.domain.model.DocumentSource;
import com.neaz.travel.visa.domain.model.SourceCheckLog;
import com.neaz.travel.visa.domain.repository.DocumentRepository;
import com.neaz.travel.visa.domain.repository.SourceCheckLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Checks every active document with an official source URL for upstream
 * changes: fetches the live content, hashes it the same way ingestion
 * hashes stored content, and compares. Detects and logs only — it never
 * re-ingests. A human curator reviews {@code CHANGED} results and
 * re-ingests manually via the existing ingestion endpoint, so nothing
 * unreviewed is ever written into the knowledge base.
 */
@Service
public class SourceChangeMonitor {

    private final DocumentRepository documentRepository;
    private final DocumentParsingPort documentParsingPort;
    private final SourceCheckLogRepository sourceCheckLogRepository;

    public SourceChangeMonitor(DocumentRepository documentRepository,
                                DocumentParsingPort documentParsingPort,
                                SourceCheckLogRepository sourceCheckLogRepository) {
        this.documentRepository = documentRepository;
        this.documentParsingPort = documentParsingPort;
        this.sourceCheckLogRepository = sourceCheckLogRepository;
    }

    /**
     * Checks every active, source-having document and logs the result of
     * each check. A failure checking one document never aborts the run for
     * the rest.
     */
    public void checkAll() {
        List<Document> documents = documentRepository.findAllActiveWithSourceUrl();
        for (Document document : documents) {
            checkOne(document);
        }
    }

    private void checkOne(Document document) {
        String sourceUrl = document.getMetadata().sourceUrl();
        try {
            String liveText = documentParsingPort.parse(new DocumentSource(sourceUrl));
            String liveHash = Sha256Hasher.hex(liveText);
            SourceCheckLog.Status status = liveHash.equals(document.getFileHash())
                    ? SourceCheckLog.Status.UNCHANGED
                    : SourceCheckLog.Status.CHANGED;
            sourceCheckLogRepository.log(document.getId(), liveHash, status);
        } catch (VisaRagException e) {
            sourceCheckLogRepository.log(document.getId(), null, SourceCheckLog.Status.FETCH_FAILED);
        }
    }
}

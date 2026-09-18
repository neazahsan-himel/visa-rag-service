package com.neaz.travel.visa.application.applicant;

import com.neaz.travel.visa.application.command.VisaQueryCommand;
import com.neaz.travel.visa.application.query.VisaQueryService;
import com.neaz.travel.visa.application.result.ApplicationResult;
import com.neaz.travel.visa.application.result.ChecklistResult;
import com.neaz.travel.visa.application.result.VisaQueryResult;
import com.neaz.travel.visa.common.exception.ApplicationNotFoundException;
import com.neaz.travel.visa.common.exception.InsufficientContextException;
import com.neaz.travel.visa.domain.model.MetadataFilter;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Answers "what documents does this application need" by asking that
 * question of the existing RAG pipeline, scoped to the application's
 * passport/destination/visa-type — no separately maintained document-list
 * data exists or is needed; the knowledge base already answers this (see
 * {@code docs/VISA_APPLICATION_ASSISTANT_PROPOSAL.md} §9 Phase D). Contains
 * no retrieval logic itself — that lives entirely behind {@link VisaQueryService}.
 *
 * <p>{@link InsufficientContextException} is caught and converted into a
 * {@link ChecklistResult} carrying the fixed fallback text, rather than left
 * to propagate — {@code GlobalExceptionHandler}'s existing handler for that
 * exception returns a {@code VisaQueryResponse}-shaped body, which does not
 * match {@code ChecklistResponse}'s shape (missing {@code applicationId}).
 */
@Service
public class ChecklistService {

    private static final String CHECKLIST_QUERY_TEMPLATE = "What documents are required for a %s visa application?";

    private static final String INSUFFICIENT_CONTEXT_MESSAGE = "I couldn't find sufficient official information.";

    private final ApplicationService applicationService;
    private final VisaQueryService visaQueryService;

    public ChecklistService(ApplicationService applicationService, VisaQueryService visaQueryService) {
        this.applicationService = applicationService;
        this.visaQueryService = visaQueryService;
    }

    /**
     * @throws ApplicationNotFoundException if no application with {@code applicationId} is owned by {@code applicantId}
     */
    public ChecklistResult getChecklist(UUID applicantId, UUID applicationId) {
        ApplicationResult application = applicationService.getOwned(applicantId, applicationId);

        MetadataFilter filter = new MetadataFilter(
                application.passportIso(), application.destinationIso(), application.visaTypeCode());
        String queryText = CHECKLIST_QUERY_TEMPLATE.formatted(application.visaTypeCode());

        try {
            VisaQueryResult queryResult = visaQueryService.answer(new VisaQueryCommand(queryText, filter));
            return new ChecklistResult(
                    applicationId, queryResult.answerText(), queryResult.citations(), queryResult.confidenceScore());
        } catch (InsufficientContextException e) {
            return new ChecklistResult(applicationId, INSUFFICIENT_CONTEXT_MESSAGE, List.of(), e.getConfidenceScore());
        }
    }
}

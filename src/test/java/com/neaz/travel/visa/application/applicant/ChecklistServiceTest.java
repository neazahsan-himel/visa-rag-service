package com.neaz.travel.visa.application.applicant;

import com.neaz.travel.visa.application.command.VisaQueryCommand;
import com.neaz.travel.visa.application.query.VisaQueryService;
import com.neaz.travel.visa.application.result.ApplicationResult;
import com.neaz.travel.visa.application.result.ChecklistResult;
import com.neaz.travel.visa.application.result.VisaQueryResult;
import com.neaz.travel.visa.common.exception.InsufficientContextException;
import com.neaz.travel.visa.domain.model.ApplicationStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link ChecklistService} scopes its generated RAG query to the
 * application's own passport/destination/visa-type, and converts a
 * below-threshold {@link InsufficientContextException} into a
 * shape-correct {@link ChecklistResult} rather than letting it propagate
 * (see the class Javadoc for why that conversion is necessary).
 */
class ChecklistServiceTest {

    private final ApplicationService applicationService = mock(ApplicationService.class);
    private final VisaQueryService visaQueryService = mock(VisaQueryService.class);
    private final ChecklistService checklistService = new ChecklistService(applicationService, visaQueryService);

    private ApplicationResult application(UUID applicantId, UUID applicationId) {
        return new ApplicationResult(applicationId, applicantId, "BD", "IN", "TOURIST", ApplicationStatus.OPEN, Instant.now());
    }

    @Test
    void getChecklist_queriesRagPipelineScopedToApplicationFilter() {
        UUID applicantId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        when(applicationService.getOwned(applicantId, applicationId)).thenReturn(application(applicantId, applicationId));
        when(visaQueryService.answer(any())).thenReturn(new VisaQueryResult("Bring your passport and a photo.", List.of(), 0.9));

        ChecklistResult result = checklistService.getChecklist(applicantId, applicationId);

        ArgumentCaptor<VisaQueryCommand> captor = ArgumentCaptor.forClass(VisaQueryCommand.class);
        verify(visaQueryService).answer(captor.capture());
        assertEquals("BD", captor.getValue().filter().passportIso());
        assertEquals("IN", captor.getValue().filter().destinationIso());
        assertEquals("TOURIST", captor.getValue().filter().visaTypeCode());
        assertEquals(applicationId, result.applicationId());
        assertEquals("Bring your passport and a photo.", result.checklistText());
    }

    @Test
    void getChecklist_whenContextInsufficient_returnsFallbackChecklistResult() {
        UUID applicantId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        when(applicationService.getOwned(applicantId, applicationId)).thenReturn(application(applicantId, applicationId));
        when(visaQueryService.answer(any())).thenThrow(new InsufficientContextException(0.2));

        ChecklistResult result = checklistService.getChecklist(applicantId, applicationId);

        assertTrue(result.citations().isEmpty());
        assertEquals(0.2, result.confidenceScore());
    }
}

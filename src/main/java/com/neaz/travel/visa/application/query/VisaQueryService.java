package com.neaz.travel.visa.application.query;

import com.neaz.travel.visa.application.command.VisaQueryCommand;
import com.neaz.travel.visa.application.port.ChatPort;
import com.neaz.travel.visa.application.query.citation.CitationBuilder;
import com.neaz.travel.visa.application.query.prompt.VisaPromptTemplate;
import com.neaz.travel.visa.application.query.retrieval.HybridRetriever;
import com.neaz.travel.visa.application.query.retrieval.ReRanker;
import com.neaz.travel.visa.application.result.VisaQueryResult;
import com.neaz.travel.visa.common.exception.InsufficientContextException;
import com.neaz.travel.visa.domain.model.Citation;
import com.neaz.travel.visa.domain.model.RetrievedContext;
import com.neaz.travel.visa.domain.model.VisaAnswer;
import com.neaz.travel.visa.domain.model.VisaQuery;
import com.neaz.travel.visa.domain.repository.VisaQueryLogRepository;
import com.neaz.travel.visa.domain.service.ConfidenceScorer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates the visa query use case end to end: hybrid retrieval, rerank,
 * confidence scoring, grounded generation, citation building, and audit
 * logging. Contains no retrieval, prompting, or LLM logic itself — that lives
 * entirely behind the collaborators it depends on.
 */
@Service
public class VisaQueryService {

    private final HybridRetriever hybridRetriever;
    private final ReRanker reRanker;
    private final VisaPromptTemplate promptTemplate;
    private final ChatPort chatPort;
    private final CitationBuilder citationBuilder;
    private final VisaQueryLogRepository visaQueryLogRepository;
    private final String chatModelId;
    private final double confidenceThreshold;

    private final ConfidenceScorer confidenceScorer = new ConfidenceScorer();

    public VisaQueryService(
            HybridRetriever hybridRetriever,
            ReRanker reRanker,
            VisaPromptTemplate promptTemplate,
            ChatPort chatPort,
            CitationBuilder citationBuilder,
            VisaQueryLogRepository visaQueryLogRepository,
            @Qualifier("chatModelId") String chatModelId,
            @Value("${rag.confidence.threshold}") double confidenceThreshold) {
        this.hybridRetriever = hybridRetriever;
        this.reRanker = reRanker;
        this.promptTemplate = promptTemplate;
        this.chatPort = chatPort;
        this.citationBuilder = citationBuilder;
        this.visaQueryLogRepository = visaQueryLogRepository;
        this.chatModelId = chatModelId;
        this.confidenceThreshold = confidenceThreshold;
    }

    /**
     * @throws InsufficientContextException if the retrieved context's
     *                                       confidence score falls below the
     *                                       configured threshold
     */
    public VisaQueryResult answer(VisaQueryCommand command) {
        Instant startedAt = Instant.now();

        VisaQuery query = new VisaQuery(command.queryText(), command.filter());

        RetrievedContext fusedContext = hybridRetriever.retrieve(query);
        RetrievedContext rerankedContext = reRanker.rerank(fusedContext);

        double confidenceScore = confidenceScorer.score(rerankedContext);
        if (confidenceScore < confidenceThreshold) {
            throw new InsufficientContextException(confidenceScore);
        }

        String prompt = promptTemplate.render(query, rerankedContext);
        String answerText = chatPort.generate(prompt, chatModelId);
        List<Citation> citations = citationBuilder.build(rerankedContext);

        VisaAnswer answer = new VisaAnswer(answerText, citations, confidenceScore);

        List<UUID> retrievedChunkIds = rerankedContext.scoredChunks().stream()
                .map(scoredChunk -> scoredChunk.chunk().getId())
                .toList();
        long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
        visaQueryLogRepository.log(query, answer, retrievedChunkIds, chatModelId, latencyMs);

        return new VisaQueryResult(answer.answerText(), answer.citations(), answer.confidenceScore());
    }
}
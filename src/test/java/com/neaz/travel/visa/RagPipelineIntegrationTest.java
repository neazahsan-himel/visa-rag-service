package com.neaz.travel.visa;

import com.neaz.travel.visa.application.command.VisaQueryCommand;
import com.neaz.travel.visa.application.ingestion.DocumentIngestionService;
import com.neaz.travel.visa.application.monitoring.PendingChangeQueryService;
import com.neaz.travel.visa.application.port.ChatPort;
import com.neaz.travel.visa.application.port.DocumentParsingPort;
import com.neaz.travel.visa.application.port.EmbeddingPort;
import com.neaz.travel.visa.application.query.VisaQueryService;
import com.neaz.travel.visa.application.query.citation.CitationBuilder;
import com.neaz.travel.visa.application.query.prompt.VisaPromptTemplate;
import com.neaz.travel.visa.application.query.retrieval.HybridRetriever;
import com.neaz.travel.visa.application.query.retrieval.ReRanker;
import com.neaz.travel.visa.common.exception.InsufficientContextException;
import com.neaz.travel.visa.common.model.ApiResponse;
import com.neaz.travel.visa.domain.model.DocumentSource;
import com.neaz.travel.visa.domain.model.MetadataFilter;
import com.neaz.travel.visa.infrastructure.document.ChunkingStrategy;
import com.neaz.travel.visa.infrastructure.persistence.ChunkJpaRepository;
import com.neaz.travel.visa.infrastructure.persistence.DocumentJpaRepository;
import com.neaz.travel.visa.infrastructure.persistence.DocumentRepositoryAdapter;
import com.neaz.travel.visa.infrastructure.persistence.QueryAuditJpaRepository;
import com.neaz.travel.visa.infrastructure.persistence.SourceCheckLogRepositoryAdapter;
import com.neaz.travel.visa.infrastructure.persistence.VisaQueryLogRepositoryAdapter;
import com.neaz.travel.visa.infrastructure.vectorstore.QdrantVectorStoreAdapter;
import com.neaz.travel.visa.presentation.controller.GlobalExceptionHandler;
import com.neaz.travel.visa.presentation.controller.IngestionController;
import com.neaz.travel.visa.presentation.controller.VisaQueryController;
import com.neaz.travel.visa.presentation.dto.IngestionRequest;
import com.neaz.travel.visa.presentation.dto.IngestionResponse;
import com.neaz.travel.visa.presentation.dto.VisaQueryRequest;
import com.neaz.travel.visa.presentation.dto.VisaQueryResponse;
import com.neaz.travel.visa.presentation.mapper.IngestionMapper;
import com.neaz.travel.visa.presentation.mapper.VisaQueryMapper;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * End-to-end integration test for the full RAG pipeline — ingestion through
 * query — against real PostgreSQL and Qdrant instances (Testcontainers), the
 * same two stores the manual smoke test (see {@code
 * docs/SPRINT5_SMOKE_TEST_REPORT.md}) ran against. It automates that report's
 * coverage: happy-path ingest-then-query, the {@code GENERAL} visa-type
 * fallback on both retrieval paths (Sprint 5's closed gap), and the
 * low-confidence fallback path the manual run never exercised.
 *
 * <p>Only {@link EmbeddingPort} and {@link ChatPort} are stubbed — a real
 * embedding/chat model would make this test non-deterministic and dependent
 * on network access or API keys, which a repeatable, CI-enforced test cannot
 * assume. The embedding stub maps a fixed set of keywords to basis vector
 * dimensions, so real Qdrant cosine similarity search still behaves
 * predictably. Every other component — chunking, persistence, vector search,
 * full-text search, RRF fusion, reranking, confidence scoring, prompt
 * rendering, citation building, and the controller/mapper layer — is real.
 *
 * <p>{@code @Transactional(propagation = NOT_SUPPORTED)} disables {@code
 * @DataJpaTest}'s default per-test rollback: {@link HybridRetriever} runs its
 * two retrieval paths on separate threads via {@code CompletableFuture}, and
 * a thread other than the one holding the test's transaction cannot see that
 * transaction's uncommitted writes. Each repository call must commit
 * immediately instead. {@link #tearDown()} clears state between tests to
 * compensate for the lost automatic rollback.
 */
@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import({DocumentRepositoryAdapter.class, VisaQueryLogRepositoryAdapter.class, SourceCheckLogRepositoryAdapter.class})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RagPipelineIntegrationTest {

    private static final String COLLECTION_NAME = "visa_chunks";
    private static final int EMBEDDING_DIMENSION = 4;
    private static final double CONFIDENCE_THRESHOLD = 0.60;

    private static final String TOURIST_DOC_PATH = "india/tourist-fee.txt";
    private static final String TOURIST_DOC_TITLE = "India Tourist Visa Checklist";
    private static final String TOURIST_DOC_TEXT =
            "The tourist visa fee for Bangladeshi passport holders is 800 BDT.";

    private static final String GENERAL_DOC_PATH = "india/general-fee.txt";
    private static final String GENERAL_DOC_TITLE = "India Visa General Information";
    private static final String GENERAL_DOC_TEXT =
            "General visa information: tourist applicants pay the same fee schedule regardless of category.";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("visa_rag_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final GenericContainer<?> QDRANT = new GenericContainer<>("qdrant/qdrant:v1.9.2")
            .withExposedPorts(6333, 6334);

    private static QdrantClient qdrantClient;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeAll
    static void startQdrant() throws Exception {
        qdrantClient = new QdrantClient(
                QdrantGrpcClient.newBuilder(QDRANT.getHost(), QDRANT.getMappedPort(6334), false).build());
        boolean exists = qdrantClient.listCollectionsAsync().get().stream().anyMatch(COLLECTION_NAME::equals);
        if (!exists) {
            qdrantClient.createCollectionAsync(COLLECTION_NAME,
                    VectorParams.newBuilder().setSize(EMBEDDING_DIMENSION).setDistance(Distance.Cosine).build()).get();
        }
    }

    @AfterAll
    static void closeQdrant() {
        qdrantClient.close();
    }

    @Autowired
    private DocumentRepositoryAdapter documentRepositoryAdapter;
    @Autowired
    private VisaQueryLogRepositoryAdapter visaQueryLogRepositoryAdapter;
    @Autowired
    private SourceCheckLogRepositoryAdapter sourceCheckLogRepositoryAdapter;
    @Autowired
    private ChunkJpaRepository chunkJpaRepository;
    @Autowired
    private DocumentJpaRepository documentJpaRepository;
    @Autowired
    private QueryAuditJpaRepository queryAuditJpaRepository;

    private IngestionController ingestionController;
    private VisaQueryController visaQueryController;
    private VisaQueryService visaQueryService;
    private CannedChatPort chatPort;

    @BeforeEach
    void setUp() {
        EmbeddingPort embeddingPort = new KeywordEmbeddingPort();
        QdrantVectorStoreAdapter vectorSearchPort = new QdrantVectorStoreAdapter(qdrantClient, chunkJpaRepository);
        DocumentParsingPort documentParsingPort = new FixedTextParsingPort(
                Map.of(TOURIST_DOC_PATH, TOURIST_DOC_TEXT, GENERAL_DOC_PATH, GENERAL_DOC_TEXT));

        DocumentIngestionService ingestionService = new DocumentIngestionService(
                documentParsingPort, new ChunkingStrategy(512, 64), embeddingPort, vectorSearchPort,
                documentRepositoryAdapter);
        PendingChangeQueryService pendingChangeQueryService = new PendingChangeQueryService(
                sourceCheckLogRepositoryAdapter, documentRepositoryAdapter);
        ingestionController = new IngestionController(
                ingestionService, pendingChangeQueryService, new IngestionMapper());

        HybridRetriever hybridRetriever = new HybridRetriever(embeddingPort, vectorSearchPort, documentRepositoryAdapter);
        chatPort = new CannedChatPort("The tourist visa fee for Bangladeshi passport holders is 800 BDT. [1]");
        visaQueryService = new VisaQueryService(
                hybridRetriever, new ReRanker(), new VisaPromptTemplate(), chatPort,
                new CitationBuilder(documentRepositoryAdapter), visaQueryLogRepositoryAdapter,
                "test-chat-model", CONFIDENCE_THRESHOLD);
        visaQueryController = new VisaQueryController(visaQueryService, new VisaQueryMapper());
    }

    @AfterEach
    void tearDown() {
        chunkJpaRepository.deleteAll();
        documentJpaRepository.deleteAll();
        queryAuditJpaRepository.deleteAll();
    }

    @Test
    void ingestThenQuery_returnsGroundedAnswerWithCitationAboveConfidenceThreshold() {
        ApiResponse<IngestionResponse> created = ingestionController.ingestDocument(
                ingestionRequest(TOURIST_DOC_PATH, TOURIST_DOC_TITLE, "TOURIST"));

        assertThat(created.success()).isTrue();
        assertThat(created.data().status()).isEqualTo("CREATED");
        assertThat(created.data().chunkCount()).isEqualTo(1);

        ApiResponse<IngestionResponse> duplicate = ingestionController.ingestDocument(
                ingestionRequest(TOURIST_DOC_PATH, TOURIST_DOC_TITLE, "TOURIST"));
        assertThat(duplicate.data().status()).isEqualTo("DUPLICATE");
        assertThat(duplicate.data().documentId()).isEqualTo(created.data().documentId());

        VisaQueryResponse answer = visaQueryController.query(
                new VisaQueryRequest("tourist visa fee", "BD", "IN", "TOURIST")).data();

        assertThat(answer.answer()).isEqualTo(chatPort.answer);
        assertThat(answer.confidenceScore()).isGreaterThanOrEqualTo(CONFIDENCE_THRESHOLD);
        assertThat(answer.citations()).hasSize(1);
        assertThat(answer.citations().get(0).ref()).isEqualTo("[1]");
        assertThat(answer.citations().get(0).source()).isEqualTo(TOURIST_DOC_TITLE);
        assertThat(chatPort.lastPrompt).contains(TOURIST_DOC_TEXT);
    }

    @Test
    void query_whenGeneralDocumentExists_includesItAlongsideTheRequestedVisaTypeOnBothRetrievalPaths() {
        ingestionController.ingestDocument(ingestionRequest(TOURIST_DOC_PATH, TOURIST_DOC_TITLE, "TOURIST"));
        ingestionController.ingestDocument(ingestionRequest(GENERAL_DOC_PATH, GENERAL_DOC_TITLE, "GENERAL"));

        VisaQueryResponse answer = visaQueryController.query(
                new VisaQueryRequest("tourist visa fee", "BD", "IN", "TOURIST")).data();

        List<String> citedSources = answer.citations().stream().map(VisaQueryResponse.CitationDto::source).toList();
        assertThat(citedSources).containsExactlyInAnyOrder(TOURIST_DOC_TITLE, GENERAL_DOC_TITLE);
    }

    @Test
    void query_whenNoDocumentsMatch_throwsInsufficientContext_andHandlerReturnsTheFallbackResponse() {
        VisaQueryCommand command = new VisaQueryCommand("tourist visa fee", new MetadataFilter("BD", "IN", "MEDICAL"));

        InsufficientContextException exception = assertThrows(InsufficientContextException.class,
                () -> visaQueryService.answer(command));

        ResponseEntity<ApiResponse<VisaQueryResponse>> handled =
                new GlobalExceptionHandler().handleInsufficientContext(exception);

        assertThat(handled.getStatusCode()).isEqualTo(HttpStatus.OK);
        VisaQueryResponse fallback = handled.getBody().data();
        assertThat(fallback.answer()).isEqualTo("I couldn't find sufficient official information.");
        assertThat(fallback.citations()).isEmpty();
        assertThat(fallback.confidenceScore()).isEqualTo(exception.getConfidenceScore());
    }

    private IngestionRequest ingestionRequest(String path, String title, String visaTypeCode) {
        IngestionRequest.MetadataDto metadata = new IngestionRequest.MetadataDto(
                "IN", "BD", visaTypeCode, "https://hci-dhaka.example/" + path, LocalDate.of(2026, 1, 1), "v1");
        return new IngestionRequest(path, title, metadata);
    }

    /**
     * Maps the presence of a fixed set of keywords to basis vector dimensions,
     * so real Qdrant cosine similarity search over these vectors behaves
     * predictably without depending on a real embedding model.
     */
    private static final class KeywordEmbeddingPort implements EmbeddingPort {
        @Override
        public float[] embed(String text) {
            String lower = text.toLowerCase(Locale.ROOT);
            return new float[]{
                    lower.contains("tourist") ? 1f : 0f,
                    lower.contains("fee") ? 1f : 0f,
                    lower.contains("business") ? 1f : 0f,
                    0f
            };
        }
    }

    private static final class FixedTextParsingPort implements DocumentParsingPort {
        private final Map<String, String> textByPath;

        private FixedTextParsingPort(Map<String, String> textByPath) {
            this.textByPath = textByPath;
        }

        @Override
        public String parse(DocumentSource source) {
            String text = textByPath.get(source.location());
            if (text == null) {
                throw new IllegalStateException("No fixture text registered for path: " + source.location());
            }
            return text;
        }
    }

    private static final class CannedChatPort implements ChatPort {
        private final String answer;
        private String lastPrompt;

        private CannedChatPort(String answer) {
            this.answer = answer;
        }

        @Override
        public String generate(String prompt, String modelId) {
            this.lastPrompt = prompt;
            return answer;
        }
    }
}

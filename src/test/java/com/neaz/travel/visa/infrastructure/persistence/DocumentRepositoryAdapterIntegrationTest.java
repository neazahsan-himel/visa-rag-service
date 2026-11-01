package com.neaz.travel.visa.infrastructure.persistence;

import com.neaz.travel.visa.domain.model.Chunk;
import com.neaz.travel.visa.domain.model.Citation;
import com.neaz.travel.visa.domain.model.Document;
import com.neaz.travel.visa.domain.model.DocumentMetadata;
import com.neaz.travel.visa.domain.model.MetadataFilter;
import com.neaz.travel.visa.domain.model.ScoredChunk;
import com.neaz.travel.visa.domain.model.VisaAnswer;
import com.neaz.travel.visa.domain.model.VisaQuery;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Integration test for {@link DocumentRepositoryAdapter} and {@link
 * VisaQueryLogRepositoryAdapter} against a real PostgreSQL instance. Uses a
 * {@code @DataJpaTest} slice rather than {@code @SpringBootTest} so that
 * {@code QdrantCollectionInitializer} and Spring AI's OpenAI auto-config —
 * neither reachable in this environment — never start.
 */
@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import({DocumentRepositoryAdapter.class, VisaQueryLogRepositoryAdapter.class})
@Testcontainers
class DocumentRepositoryAdapterIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("visa_rag_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private DocumentRepositoryAdapter documentRepositoryAdapter;

    @Autowired
    private VisaQueryLogRepositoryAdapter visaQueryLogRepositoryAdapter;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void save_whenDocumentHasChunks_thenFindByIdReturnsChunksInOriginalOrder() {
        Document document = aDocumentWithTwoChunks();

        documentRepositoryAdapter.save(document);
        Optional<Document> found = documentRepositoryAdapter.findById(document.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo(document.getTitle());
        assertThat(found.get().getMetadata()).isEqualTo(document.getMetadata());
        assertThat(found.get().getChunks()).hasSize(2);
        assertThat(found.get().getChunks().get(0).getChunkIndex()).isZero();
        assertThat(found.get().getChunks().get(1).getChunkIndex()).isEqualTo(1);
    }

    @Test
    void findByFileHash_whenDocumentSaved_returnsSameDocument() {
        Document document = aDocumentWithTwoChunks();
        documentRepositoryAdapter.save(document);

        Optional<Document> found = documentRepositoryAdapter.findByFileHash(document.getFileHash());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(document.getId());
    }

    @Test
    void fullTextSearch_whenQueryMatchesChunkContent_returnsScoredChunkForThatDocument() {
        Document document = aDocumentWithTwoChunks();
        documentRepositoryAdapter.save(document);
        MetadataFilter filter = new MetadataFilter("BD", "IN", "TOURIST");

        List<ScoredChunk> results = documentRepositoryAdapter.fullTextSearch("tourist visa fee", filter, 10);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).chunk().getDocumentId()).isEqualTo(document.getId());
        assertThat(results.get(0).fusionScore()).isGreaterThan(0.0);
    }

    @Test
    void fullTextSearch_whenVisaTypeFilterDoesNotMatch_returnsNoResults() {
        Document document = aDocumentWithTwoChunks();
        documentRepositoryAdapter.save(document);
        MetadataFilter filter = new MetadataFilter("BD", "IN", "BUSINESS");

        List<ScoredChunk> results = documentRepositoryAdapter.fullTextSearch("tourist visa fee", filter, 10);

        assertThat(results).isEmpty();
    }

    @Test
    void log_whenQueryProcessed_persistsAuditRowWithoutError() {
        VisaQuery query = new VisaQuery("What is the tourist visa fee?",
                new MetadataFilter("BD", "IN", "TOURIST"));
        VisaAnswer answer = new VisaAnswer(
                "The tourist visa fee for Bangladeshi passport holders is 800 BDT.",
                List.of(new Citation("[1]", "India Tourist Visa Guide", "https://indianvisaonline.gov.in",
                        LocalDate.of(2026, 1, 1), UUID.randomUUID())),
                0.87);

        visaQueryLogRepositoryAdapter.log(query, answer, List.of(UUID.randomUUID()), "gpt-4o-mini", 420L);
    }

    @Test
    void replace_whenSameDocumentKey_deactivatesSupersededAndLeavesReplacementActive() {
        Document superseded = aDocumentWithTwoChunks("in/bd/tourist/guide");
        documentRepositoryAdapter.save(superseded);
        Document replacement = aDocumentWithTwoChunks("in/bd/tourist/guide");

        superseded.deactivate();
        documentRepositoryAdapter.replace(superseded, replacement);
        entityManager.flush();

        assertThat(documentRepositoryAdapter.findActiveByDocumentKey("in/bd/tourist/guide"))
                .map(Document::getId)
                .contains(replacement.getId());
        assertThat(documentRepositoryAdapter.findById(superseded.getId()))
                .map(Document::isActive)
                .contains(false);
    }

    @Test
    void save_whenAnotherActiveDocumentHasSameKey_isRejectedByTheDatabase() {
        documentRepositoryAdapter.save(aDocumentWithTwoChunks("in/bd/tourist/guide"));
        entityManager.flush();

        documentRepositoryAdapter.save(aDocumentWithTwoChunks("in/bd/tourist/guide"));

        assertThatThrownBy(entityManager::flush).isInstanceOf(PersistenceException.class);
    }

    private Document aDocumentWithTwoChunks() {
        return aDocumentWithTwoChunks("in/bd/tourist/guide");
    }

    private Document aDocumentWithTwoChunks(String documentKey) {
        UUID documentId = UUID.randomUUID();
        DocumentMetadata metadata = new DocumentMetadata(documentKey, "IN", "BD", "TOURIST",
                "https://indianvisaonline.gov.in", LocalDate.of(2026, 1, 1), "v1");
        Chunk chunk0 = new Chunk(UUID.randomUUID(), documentId, 0,
                "The tourist visa fee for Bangladeshi passport holders is 800 BDT.", 12, UUID.randomUUID());
        Chunk chunk1 = new Chunk(UUID.randomUUID(), documentId, 1,
                "Processing time for a tourist visa is typically 3 to 5 working days.", 14, UUID.randomUUID());
        return new Document(documentId, "India Tourist Visa Guide", UUID.randomUUID().toString(), metadata,
                List.of(chunk0, chunk1), Instant.now(), true);
    }
}
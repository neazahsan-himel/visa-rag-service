package com.neaz.travel.visa.infrastructure.persistence;

import com.neaz.travel.visa.domain.model.FeeComponent;
import com.neaz.travel.visa.domain.model.VisaFee;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Integration test for {@link VisaFeeRepositoryAdapter} against a real
 * PostgreSQL instance — same {@code @DataJpaTest} + Testcontainers pattern as
 * {@link DocumentRepositoryAdapterIntegrationTest}. Flyway applies the real
 * migrations (including {@code V17}), and Hibernate's {@code validate} mode
 * checks the fee entities against them. Test fees use the {@code TOURIST}
 * visa type so they never collide with seeded {@code GENERAL} fee data.
 */
@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(VisaFeeRepositoryAdapter.class)
@Testcontainers
class VisaFeeRepositoryAdapterIntegrationTest {

    private static final LocalDate VERIFIED_AT = LocalDate.of(2026, 11, 1);
    private static final Currency BDT = Currency.getInstance("BDT");

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
    private VisaFeeRepositoryAdapter visaFeeRepositoryAdapter;

    @Autowired
    private CountryJpaRepository countryJpaRepository;

    @Autowired
    private VisaTypeJpaRepository visaTypeJpaRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findActive_whenActiveFeeExists_returnsFeeWithComponentsInPolicyOrder() {
        UUID feeId = persistFee("TOURIST", true);
        // Persisted PROCESSING before BASIC to prove ordering is not insertion order.
        persistComponent(feeId, "PROCESSING", "Indian Visa Application Centre", "800");
        persistComponent(feeId, "BASIC", "Government of India", "0");
        entityManager.flush();
        entityManager.clear();

        Optional<VisaFee> found = visaFeeRepositoryAdapter.findActive("BD", "IN", "TOURIST");

        assertThat(found).isPresent();
        VisaFee fee = found.get();
        assertThat(fee.id()).isEqualTo(feeId);
        assertThat(fee.passportIso()).isEqualTo("BD");
        assertThat(fee.destinationIso()).isEqualTo("IN");
        assertThat(fee.visaTypeCode()).isEqualTo("TOURIST");
        assertThat(fee.sourceUrl()).isEqualTo("https://www.hcidhaka.gov.in/");
        assertThat(fee.sourceQuote()).isEqualTo("test quote");
        assertThat(fee.verifiedAt()).isEqualTo(VERIFIED_AT);
        assertThat(fee.components()).containsExactly(
                new FeeComponent(FeeComponent.Type.BASIC, "Government of India", BigDecimal.ZERO, BDT),
                new FeeComponent(FeeComponent.Type.PROCESSING, "Indian Visa Application Centre",
                        new BigDecimal("800"), BDT));
    }

    @Test
    void findActive_whenOnlyInactiveFeeExists_returnsEmpty() {
        UUID feeId = persistFee("TOURIST", false);
        persistComponent(feeId, "PROCESSING", "Indian Visa Application Centre", "800");
        entityManager.flush();

        assertThat(visaFeeRepositoryAdapter.findActive("BD", "IN", "TOURIST")).isEmpty();
    }

    @Test
    void findActive_isExactMatchOnVisaType() {
        UUID feeId = persistFee("TOURIST", true);
        persistComponent(feeId, "PROCESSING", "Indian Visa Application Centre", "800");
        entityManager.flush();

        assertThat(visaFeeRepositoryAdapter.findActive("BD", "IN", "BUSINESS")).isEmpty();
    }

    @Test
    void findActive_isScopedByPassport() {
        UUID feeId = persistFee("TOURIST", true);
        persistComponent(feeId, "PROCESSING", "Indian Visa Application Centre", "800");
        entityManager.flush();

        assertThat(visaFeeRepositoryAdapter.findActive("US", "IN", "TOURIST")).isEmpty();
    }

    @Test
    void findActive_withUnknownDestination_returnsEmpty() {
        assertThat(visaFeeRepositoryAdapter.findActive("BD", "ZZ", "TOURIST")).isEmpty();
    }

    @Test
    void findActive_withUnknownVisaType_returnsEmpty() {
        assertThat(visaFeeRepositoryAdapter.findActive("BD", "IN", "NO_SUCH_TYPE")).isEmpty();
    }

    private UUID persistFee(String visaTypeCode, boolean active) {
        Long countryId = countryJpaRepository.findByIsoCode("IN").orElseThrow().getId();
        Long visaTypeId = visaTypeJpaRepository.findByCountryIdAndCode(countryId, visaTypeCode).orElseThrow().getId();
        UUID id = UUID.randomUUID();
        entityManager.persist(new VisaFeeJpaEntity(id, countryId, visaTypeId, "BD", "https://www.hcidhaka.gov.in/",
                "test quote", VERIFIED_AT, active, Instant.now()));
        return id;
    }

    private void persistComponent(UUID feeId, String type, String chargedBy, String amount) {
        entityManager.persist(new VisaFeeComponentJpaEntity(UUID.randomUUID(), feeId, type, chargedBy,
                new BigDecimal(amount), "BDT"));
    }
}

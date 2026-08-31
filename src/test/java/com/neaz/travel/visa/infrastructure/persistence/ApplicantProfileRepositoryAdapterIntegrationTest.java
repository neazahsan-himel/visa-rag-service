package com.neaz.travel.visa.infrastructure.persistence;

import com.neaz.travel.visa.domain.model.Address;
import com.neaz.travel.visa.domain.model.ApplicantProfile;
import com.neaz.travel.visa.domain.model.ExtractedField;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Integration test for {@link ApplicantProfileRepositoryAdapter} against a
 * real PostgreSQL instance — same {@code @DataJpaTest} + Testcontainers
 * pattern as {@link DocumentRepositoryAdapterIntegrationTest}. Round-trips
 * every field type ({@code String}, {@code LocalDate}, {@code Integer},
 * {@link Address}) to prove the text serialization and the {@code V10}/
 * {@code V11} migrations agree with each other, and verifies the
 * delete-then-reinsert save strategy replaces fields rather than
 * accumulating duplicate rows on a second save.
 */
@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(ApplicantProfileRepositoryAdapter.class)
@Testcontainers
class ApplicantProfileRepositoryAdapterIntegrationTest {

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
    private ApplicantProfileRepositoryAdapter applicantProfileRepositoryAdapter;

    @Test
    void save_whenProfileHasEveryFieldType_thenFindByIdRoundTripsAllValues() {
        UUID applicantId = UUID.randomUUID();
        Instant now = Instant.now();
        ApplicantProfile profile = new ApplicantProfile(UUID.randomUUID(), applicantId, now, now, Map.of(), null);
        profile.setField(ApplicantProfile.PATH_PERSONAL_FULL_NAME, ExtractedField.selfDeclared("Jane Doe"));
        profile.setField(ApplicantProfile.PATH_PERSONAL_DATE_OF_BIRTH,
                ExtractedField.selfDeclared(LocalDate.of(1990, 1, 15)));
        profile.setField(ApplicantProfile.PATH_INTENDED_DURATION_DAYS, ExtractedField.selfDeclared(14));
        profile.setField(ApplicantProfile.PATH_PERMANENT_ADDRESS,
                ExtractedField.selfDeclared(new Address("House 1", null, "Dhaka", "1200", "BD")));
        profile.setPurposeOfVisit("Tourism");

        applicantProfileRepositoryAdapter.save(profile);
        Optional<ApplicantProfile> found = applicantProfileRepositoryAdapter.findById(profile.getId());

        assertThat(found).isPresent();
        assertThat(found.get().personalInfo().fullName().normalizedValue()).isEqualTo("Jane Doe");
        assertThat(found.get().personalInfo().dateOfBirth().normalizedValue()).isEqualTo(LocalDate.of(1990, 1, 15));
        assertThat(found.get().travelInfo().intendedDurationDays().normalizedValue()).isEqualTo(14);
        assertThat(found.get().addressInfo().permanentAddress().normalizedValue())
                .isEqualTo(new Address("House 1", null, "Dhaka", "1200", "BD"));
        assertThat(found.get().getPurposeOfVisit()).isEqualTo("Tourism");
    }

    @Test
    void save_thenFindByApplicantId_returnsSameProfile() {
        UUID applicantId = UUID.randomUUID();
        Instant now = Instant.now();
        ApplicantProfile profile = new ApplicantProfile(UUID.randomUUID(), applicantId, now, now, Map.of(), null);
        profile.setField(ApplicantProfile.PATH_PERSONAL_FULL_NAME, ExtractedField.selfDeclared("Jane Doe"));

        applicantProfileRepositoryAdapter.save(profile);
        Optional<ApplicantProfile> found = applicantProfileRepositoryAdapter.findByApplicantId(applicantId);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(profile.getId());
    }

    @Test
    void save_whenCalledTwiceWithFewerFields_replacesFieldsRatherThanAccumulating() {
        UUID applicantId = UUID.randomUUID();
        Instant now = Instant.now();
        ApplicantProfile profile = new ApplicantProfile(UUID.randomUUID(), applicantId, now, now, Map.of(), null);
        profile.setField(ApplicantProfile.PATH_PERSONAL_FULL_NAME, ExtractedField.selfDeclared("Jane Doe"));
        profile.setField(ApplicantProfile.PATH_PERSONAL_GENDER, ExtractedField.selfDeclared("F"));
        applicantProfileRepositoryAdapter.save(profile);

        ApplicantProfile reloaded = applicantProfileRepositoryAdapter.findById(profile.getId()).orElseThrow();
        reloaded.setField(ApplicantProfile.PATH_PERSONAL_FULL_NAME, ExtractedField.selfDeclared("Jane D. Doe"));
        applicantProfileRepositoryAdapter.save(reloaded);

        ApplicantProfile refound = applicantProfileRepositoryAdapter.findById(profile.getId()).orElseThrow();
        assertThat(refound.getFields()).hasSize(2);
        assertThat(refound.personalInfo().fullName().normalizedValue()).isEqualTo("Jane D. Doe");
        assertThat(refound.personalInfo().gender().normalizedValue()).isEqualTo("F");
    }

    @Test
    void findByApplicantId_whenNoProfileExists_returnsEmpty() {
        Optional<ApplicantProfile> found = applicantProfileRepositoryAdapter.findByApplicantId(UUID.randomUUID());

        assertThat(found).isEmpty();
    }
}

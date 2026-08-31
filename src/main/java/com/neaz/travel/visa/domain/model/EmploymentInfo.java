package com.neaz.travel.visa.domain.model;

/**
 * Read-only view of an {@link ApplicantProfile}'s employment fields,
 * assembled from its internal field map. Any component is {@code null} if
 * that field hasn't been set on the profile yet.
 *
 * @param occupation      the applicant's occupation/job title
 * @param employerName    the applicant's employer
 * @param employerAddress the employer's address
 * @param monthlyIncome   monthly income, as stated; {@code null} if not applicable to this visa type
 */
public record EmploymentInfo(
        ExtractedField<String> occupation,
        ExtractedField<String> employerName,
        ExtractedField<String> employerAddress,
        ExtractedField<String> monthlyIncome) {
}

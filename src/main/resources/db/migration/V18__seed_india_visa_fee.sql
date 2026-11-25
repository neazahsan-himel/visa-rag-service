-- Official India visa fee for Bangladeshi passport holders.
--
-- Source: High Commission of India, Dhaka — visa FAQ, Q3 (page last updated
-- 2026-10-29; re-verified 2026-11-01). Recorded in
-- knowledge-base/India/General/hci-dhaka-faq.md. The FAQ page itself has an
-- unstable, encrypted URL, so the HCI Dhaka homepage it is linked from is used
-- as source_url, matching that document's ingestion metadata.
--
-- The quote is the original Bengali, which the knowledge base treats as
-- authoritative. In English: "No visa fee is charged by the Government of
-- India for Bangladeshis. The Indian Visa Application Centre charges a nominal
-- processing fee of 800 Taka. No additional fee is charged."
--
-- The figure is flat across visa types (no per-type breakdown is published),
-- so it is recorded once under GENERAL. No SPECIAL component is recorded
-- because the source does not mention one.
WITH new_fee AS (
    INSERT INTO visa_fees (country_id, visa_type_id, passport_iso, source_url, source_quote, verified_at)
    SELECT countries.id, visa_types.id, 'BD',
           'https://www.hcidhaka.gov.in/',
           'বাংলাদেশীদের জন্য ভারত সরকার কর্তৃক কোনো ভিসা ফি নেয়া হয় না। ভারতীয় ভিসা আবেদন কেন্দ্র নামমাত্র ৮০০ টাকা প্রক্রিয়াকরণ ফি নিয়ে থাকে। অতিরিক্ত কোন ফি নেয়া হয় না।',
           DATE '2026-11-01'
    FROM countries
    JOIN visa_types ON visa_types.country_id = countries.id AND visa_types.code = 'GENERAL'
    WHERE countries.iso_code = 'IN'
    RETURNING id
)
INSERT INTO visa_fee_components (visa_fee_id, component_type, charged_by, amount, currency)
SELECT new_fee.id, c.component_type, c.charged_by, c.amount, 'BDT'
FROM new_fee
CROSS JOIN (VALUES
    ('BASIC',      'Government of India',            0.00),
    ('PROCESSING', 'Indian Visa Application Centre', 800.00)
) AS c(component_type, charged_by, amount);

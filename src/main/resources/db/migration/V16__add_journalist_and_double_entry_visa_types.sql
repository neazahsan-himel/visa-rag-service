-- Visa categories introduced in the High Commission of India, Dhaka checklist
-- "Visa Documents: Category-wise" (Visa_Category_Wise_Documents_Required_13082026.pdf,
-- PDF created 2026-08-12), which superseded the 2024 checklist the original
-- India visa types were seeded from.
INSERT INTO visa_types (country_id, code, name)
SELECT countries.id, v.code, v.name
FROM countries
CROSS JOIN (VALUES
    ('JOURNALIST', 'Journalist Visa (J-1)'),
    ('DOUBLE_ENTRY', 'Double Entry Visa (X-3)')
) AS v(code, name)
WHERE countries.iso_code = 'IN';

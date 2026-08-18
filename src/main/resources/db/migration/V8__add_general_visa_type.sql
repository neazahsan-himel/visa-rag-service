INSERT INTO visa_types (country_id, code, name)
SELECT id, code, name
FROM countries
CROSS JOIN (VALUES
    ('GENERAL', 'General / Cross-Category Visa Information')
) AS v(code, name)
WHERE countries.iso_code = 'IN';
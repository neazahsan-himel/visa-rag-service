INSERT INTO visa_types (country_id, code, name)
SELECT countries.id, v.code, v.name
FROM countries
CROSS JOIN (VALUES
    ('GENERAL', 'General / Cross-Category Visa Information')
) AS v(code, name)
WHERE countries.iso_code = 'IN';
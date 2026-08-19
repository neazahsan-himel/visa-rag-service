INSERT INTO countries (iso_code, name) VALUES
    ('BD', 'Bangladesh'),
    ('IN', 'India');

INSERT INTO visa_types (country_id, code, name)
SELECT countries.id, v.code, v.name
FROM countries
CROSS JOIN (VALUES
    ('TOURIST', 'Tourist Visa'),
    ('BUSINESS', 'Business Visa'),
    ('MEDICAL', 'Medical Visa'),
    ('STUDENT', 'Student Visa')
) AS v(code, name)
WHERE countries.iso_code = 'IN';
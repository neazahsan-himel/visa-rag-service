INSERT INTO visa_types (country_id, code, name)
SELECT id, code, name
FROM countries
CROSS JOIN (VALUES
    ('TRANSIT', 'Transit Visa'),
    ('MUKTIJODDHA', 'Muktijoddha (Freedom Fighter) Visa'),
    ('ENTRY', 'Entry Visa'),
    ('DEFENSE', 'Defense/Paramilitary/Police Personnel Visa'),
    ('CONFERENCE', 'Conference Visa'),
    ('EMPLOYMENT', 'Employment Visa')
) AS v(code, name)
WHERE countries.iso_code = 'IN';
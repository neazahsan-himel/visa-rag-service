# India Visa — Categories Overview (as listed by the two official portals)

**Source:** Government of India Visa Online / Indian Visa Application Centre, Bangladesh
**Source URLs:**
- https://indianvisaonline.gov.in/visa/visa-category.html
- https://indianvisa-bangladesh.nic.in/visa/ (Visa Categories section)
**Country:** India (IN)
**Passport:** Bangladesh (BD)
**Visa type:** GENERAL (applies to all visa types)
**Document version:** Not specified on source
**Retrieved:** 2026-08-06 (no "last updated" date is shown on the source pages, so the retrieval date is used as `lastUpdated`)

**Note:** the two official portals list slightly different sets of category names. Both are reproduced separately below, verbatim per source, rather than merged into one list — merging could misattribute a category to the wrong portal or imply agreement between sources that isn't actually stated.

## As listed on indianvisa-bangladesh.nic.in

"Government of India issues the following visas": Business Visa, Conference Visa, Diplomatic Visa, Employment Visa, Emergency Visa, Entry Visa, Journalist Visa, Medical Visa, Missionaries Visa, Permit to re-enter within 2 months, Research Visa, Student Visa, Tourist Visa, Transit Visa.

## As listed on indianvisaonline.gov.in

**Regular/paper visa categories:** Business Visa, Diplomatic Visa, Work Visa, Family Visa (for dependents), Journalist Visa, Medical Visa, Official Visa, Pakistan Specific Visa, Student Visa, Tourist Visa, Transit Visa, Miscellaneous Visa.

**e-Visa categories:** e-Tourist, e-Business, e-Medical, e-Medical Attendant, e-Conference.

## Reconciliation with this system's `visa_types` table

This system currently models 10 India visa types (`TOURIST`, `BUSINESS`, `MEDICAL`, `STUDENT`, `TRANSIT`, `MUKTIJODDHA`, `ENTRY`, `DEFENSE`, `CONFERENCE`, `EMPLOYMENT`, `GENERAL`), sourced primarily from the HCI Dhaka document checklist. Categories named only on these two portal pages and not yet in `visa_types` — e.g. Diplomatic, Work, Family, Official, Pakistan Specific, Missionaries, Permit to re-enter, Research, Miscellaneous, and the e-Visa sub-categories — are **not yet modeled** and would need their own schema decision before dedicated content could be ingested for them.
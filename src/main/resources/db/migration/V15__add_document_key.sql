ALTER TABLE documents ADD COLUMN document_key VARCHAR(200);

-- Rows ingested before document keys existed get a unique placeholder so the
-- column can be NOT NULL; re-ingesting them with a real key supersedes nothing.
UPDATE documents SET document_key = 'legacy/' || id;

ALTER TABLE documents ALTER COLUMN document_key SET NOT NULL;

CREATE UNIQUE INDEX idx_documents_active_key ON documents(document_key) WHERE active;

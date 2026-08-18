package com.neaz.travel.visa.infrastructure.document;

import com.neaz.travel.visa.common.exception.VisaRagException;
import com.neaz.travel.visa.domain.model.DocumentSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads a document's raw bytes from the local filesystem, resolving
 * {@link DocumentSource#location()} against the configured knowledge-base
 * root. Rejects any path that would escape that root.
 */
@Component
public class FileSystemDocumentLoader {

    private final Path knowledgeBaseRoot;

    public FileSystemDocumentLoader(@Value("${knowledge-base.root}") String knowledgeBaseRoot) {
        this.knowledgeBaseRoot = Path.of(knowledgeBaseRoot).toAbsolutePath().normalize();
    }

    /**
     * Loads the raw bytes for the given source.
     *
     * @param source identifies the document's location, relative to the knowledge-base root
     * @return the document's raw bytes
     * @throws VisaRagException if the resolved path escapes the knowledge-base
     *                          root, or the file cannot be read
     */
    public byte[] load(DocumentSource source) {
        Path resolved = knowledgeBaseRoot.resolve(source.location()).normalize();
        if (!resolved.startsWith(knowledgeBaseRoot)) {
            throw new VisaRagException("Document path escapes knowledge-base root: " + source.location());
        }
        try {
            return Files.readAllBytes(resolved);
        } catch (IOException e) {
            throw new VisaRagException("Failed to load document at path: " + source.location(), e);
        }
    }
}
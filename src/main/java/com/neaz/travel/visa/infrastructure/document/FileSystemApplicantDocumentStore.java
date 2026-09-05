package com.neaz.travel.visa.infrastructure.document;

import com.neaz.travel.visa.application.port.DocumentStoragePort;
import com.neaz.travel.visa.common.exception.VisaRagException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Persists uploaded applicant-document bytes to the local filesystem, under
 * {@code <storage-root>/<applicantId>/<documentId>}. The stored reference is
 * relative to the configured root (not an absolute path), matching
 * {@link FileSystemDocumentLoader}'s resolve-against-root pattern.
 */
@Component
public class FileSystemApplicantDocumentStore implements DocumentStoragePort {

    private final Path storageRoot;

    public FileSystemApplicantDocumentStore(@Value("${applicant-documents.storage-root}") String storageRoot) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    @Override
    public String store(UUID applicantId, UUID documentId, byte[] imageBytes) {
        String relativePath = applicantId + "/" + documentId;
        Path resolved = storageRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new VisaRagException("Resolved storage path escapes applicant-documents root: " + relativePath);
        }
        try {
            Files.createDirectories(resolved.getParent());
            Files.write(resolved, imageBytes);
        } catch (IOException e) {
            throw new VisaRagException("Failed to store applicant document at path: " + relativePath, e);
        }
        return relativePath;
    }
}

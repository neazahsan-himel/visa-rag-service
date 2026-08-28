package com.neaz.travel.visa.common.util;

import com.neaz.travel.visa.common.exception.VisaRagException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hashes text with SHA-256, hex-encoded. Used everywhere a document's
 * extracted text must be fingerprinted the same way — ingestion dedup
 * ({@code Document.fileHash}) and source-change detection ({@code
 * SourceCheckLog.detectedHash}) both depend on producing byte-identical
 * output for identical input.
 */
public final class Sha256Hasher {

    private Sha256Hasher() {
    }

    /**
     * Hashes the given text with SHA-256.
     *
     * @param text the text to hash
     * @return the hash, as lowercase hex
     */
    public static String hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new VisaRagException("SHA-256 algorithm not available", e);
        }
    }
}

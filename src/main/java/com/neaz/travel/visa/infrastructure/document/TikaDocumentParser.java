package com.neaz.travel.visa.infrastructure.document;

import com.neaz.travel.visa.application.port.DocumentParsingPort;
import com.neaz.travel.visa.common.exception.VisaRagException;
import com.neaz.travel.visa.domain.model.DocumentSource;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Extracts plain text from PDF, DOCX, and HTML documents via Apache Tika's
 * auto-detecting facade. Raw bytes are supplied by {@link FileSystemDocumentLoader}.
 */
@Component
public class TikaDocumentParser implements DocumentParsingPort {

    private final FileSystemDocumentLoader documentLoader;
    private final Tika tika;

    public TikaDocumentParser(FileSystemDocumentLoader documentLoader) {
        this.documentLoader = documentLoader;
        this.tika = new Tika();
        // Our sources are curated official documents deliberately placed in
        // knowledge-base/, not arbitrary uploads — truncating at Tika's
        // default 100,000-character safeguard would silently drop content.
        this.tika.setMaxStringLength(-1);
    }

    @Override
    public String parse(DocumentSource source) {
        byte[] content = documentLoader.load(source);
        try {
            return tika.parseToString(new ByteArrayInputStream(content));
        } catch (IOException | TikaException e) {
            throw new VisaRagException("Failed to parse document: " + source.location(), e);
        }
    }
}
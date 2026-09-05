package com.neaz.travel.visa.infrastructure.document.vision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neaz.travel.visa.application.port.VisionExtractionPort;
import com.neaz.travel.visa.domain.model.VisualFieldReading;
import com.neaz.travel.visa.domain.model.VisualPassportData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

/**
 * Reads visual passport-page fields via the same {@link ChatModel} bean
 * {@code ChatAdapter} uses for RAG answer generation (currently {@code
 * gpt-4o-mini}, which is vision-capable) — no separate provider or API key.
 * Asks the model for a fixed JSON shape and parses it defensively: any
 * response that isn't valid JSON in that shape, or any failure calling the
 * model at all, degrades to {@link VisualPassportData#empty()} rather than
 * throwing, so a vision hiccup never blocks the MRZ-derived candidates the
 * caller already has.
 */
@Component
public class VisionPassportExtractionAdapter implements VisionExtractionPort {

    private static final Logger log = LoggerFactory.getLogger(VisionPassportExtractionAdapter.class);

    private static final String EXTRACTION_PROMPT = """
            You are reading the visual bio-data page of a passport (not the machine-readable zone at the bottom).
            Report exactly these eight fields as they are printed on the page:
            - passportNumber
            - dateOfBirth (ISO-8601 yyyy-MM-dd)
            - expiryDate (ISO-8601 yyyy-MM-dd)
            - issueDate (ISO-8601 yyyy-MM-dd)
            - placeOfBirth
            - passportType (the document type/code printed on the page, e.g. "P")
            - surname (the holder's surname/family name, exactly as printed)
            - givenNames (the holder's given name(s), exactly as printed)

            Respond with ONLY a JSON object, no other text, in exactly this shape:
            {
              "passportNumber": {"value": "<string or null>", "readable": <true|false>},
              "dateOfBirth": {"value": "<yyyy-MM-dd or null>", "readable": <true|false>},
              "expiryDate": {"value": "<yyyy-MM-dd or null>", "readable": <true|false>},
              "issueDate": {"value": "<yyyy-MM-dd or null>", "readable": <true|false>},
              "placeOfBirth": {"value": "<string or null>", "readable": <true|false>},
              "passportType": {"value": "<string or null>", "readable": <true|false>},
              "surname": {"value": "<string or null>", "readable": <true|false>},
              "givenNames": {"value": "<string or null>", "readable": <true|false>}
            }

            If a field is not visible on the page, or you are not confident you read it correctly, set its
            "value" to null and "readable" to false. Never guess, infer, or autocomplete a value you cannot
            actually read. "readable" means the field's label and value were both clearly legible.
            """;

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VisionPassportExtractionAdapter(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public VisualPassportData extract(byte[] imageBytes) {
        String rawResponse;
        try {
            rawResponse = callVisionModel(imageBytes);
        } catch (RuntimeException e) {
            log.warn("Vision extraction call failed; proceeding with no visual candidates", e);
            return VisualPassportData.empty();
        }
        return parse(rawResponse);
    }

    private String callVisionModel(byte[] imageBytes) {
        Media media = Media.builder()
                .mimeType(detectMimeType(imageBytes))
                .data(new ByteArrayResource(imageBytes))
                .build();
        UserMessage userMessage = UserMessage.builder().text(EXTRACTION_PROMPT).media(media).build();
        return chatModel.call(new Prompt(userMessage)).getResult().getOutput().getText();
    }

    private static MimeType detectMimeType(byte[] imageBytes) {
        boolean isPng = imageBytes.length > 4 && (imageBytes[0] & 0xFF) == 0x89 && imageBytes[1] == 'P'
                && imageBytes[2] == 'N' && imageBytes[3] == 'G';
        return isPng ? MimeTypeUtils.IMAGE_PNG : MimeTypeUtils.IMAGE_JPEG;
    }

    private VisualPassportData parse(String rawResponse) {
        JsonNode root;
        try {
            root = objectMapper.readTree(stripCodeFence(rawResponse));
        } catch (Exception e) {
            log.warn("Vision model response was not valid JSON; proceeding with no visual candidates");
            return VisualPassportData.empty();
        }
        return new VisualPassportData(
                readField(root, "passportNumber"),
                readField(root, "dateOfBirth"),
                readField(root, "expiryDate"),
                readField(root, "issueDate"),
                readField(root, "placeOfBirth"),
                readField(root, "passportType"),
                readField(root, "surname"),
                readField(root, "givenNames"));
    }

    private static String stripCodeFence(String response) {
        String trimmed = response.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private static VisualFieldReading readField(JsonNode root, String key) {
        JsonNode fieldNode = root.get(key);
        if (fieldNode == null || fieldNode.isNull()) {
            return VisualFieldReading.NOT_FOUND;
        }
        JsonNode valueNode = fieldNode.get("value");
        String value = (valueNode == null || valueNode.isNull()) ? null : valueNode.asText().trim();
        if (value == null || value.isBlank()) {
            return VisualFieldReading.NOT_FOUND;
        }
        boolean readable = fieldNode.path("readable").asBoolean(false);
        return new VisualFieldReading(value, readable);
    }
}

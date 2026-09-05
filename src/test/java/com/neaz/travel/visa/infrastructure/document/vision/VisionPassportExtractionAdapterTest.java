package com.neaz.travel.visa.infrastructure.document.vision;

import com.neaz.travel.visa.domain.model.VisualPassportData;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

/**
 * Verifies the adapter parses the vision model's JSON contract defensively:
 * well-formed JSON (with and without a markdown code fence), and — since a
 * chat model's raw text output is never guaranteed to be valid JSON — a
 * degraded response that falls back to "no candidates" rather than
 * throwing or fabricating a value.
 */
class VisionPassportExtractionAdapterTest {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final VisionPassportExtractionAdapter adapter = new VisionPassportExtractionAdapter(chatModel);

    private void stubResponse(String text) {
        ChatResponse response = new ChatResponse(java.util.List.of(new Generation(new AssistantMessage(text))));
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
    }

    @Test
    void extract_wellFormedJson_readsEveryField() {
        stubResponse("""
                {
                  "passportNumber": {"value": "A1234567", "readable": true},
                  "dateOfBirth": {"value": "1990-05-15", "readable": true},
                  "expiryDate": {"value": "2030-05-15", "readable": false},
                  "issueDate": {"value": "2020-05-15", "readable": true},
                  "placeOfBirth": {"value": "Dhaka", "readable": true},
                  "passportType": {"value": "P", "readable": true}
                }
                """);

        VisualPassportData result = adapter.extract(new byte[] {1, 2, 3});

        assertEquals("A1234567", result.passportNumber().rawValue());
        assertTrue(result.passportNumber().clearlyReadable());
        assertEquals("2030-05-15", result.expiryDate().rawValue());
        assertFalse(result.expiryDate().clearlyReadable());
        assertEquals("Dhaka", result.placeOfBirth().rawValue());
    }

    @Test
    void extract_jsonWrappedInMarkdownCodeFence_stripsAndParses() {
        stubResponse("""
                ```json
                {
                  "passportNumber": {"value": "A1234567", "readable": true},
                  "dateOfBirth": {"value": null, "readable": false},
                  "expiryDate": {"value": null, "readable": false},
                  "issueDate": {"value": null, "readable": false},
                  "placeOfBirth": {"value": null, "readable": false},
                  "passportType": {"value": null, "readable": false}
                }
                ```
                """);

        VisualPassportData result = adapter.extract(new byte[] {1, 2, 3});

        assertEquals("A1234567", result.passportNumber().rawValue());
        assertFalse(result.dateOfBirth().isPresent());
    }

    @Test
    void extract_fieldNotVisible_isNotFound() {
        stubResponse("""
                {
                  "passportNumber": {"value": null, "readable": false},
                  "dateOfBirth": {"value": null, "readable": false},
                  "expiryDate": {"value": null, "readable": false},
                  "issueDate": {"value": null, "readable": false},
                  "placeOfBirth": {"value": null, "readable": false},
                  "passportType": {"value": null, "readable": false}
                }
                """);

        VisualPassportData result = adapter.extract(new byte[] {1, 2, 3});

        assertFalse(result.issueDate().isPresent());
        assertFalse(result.placeOfBirth().isPresent());
        assertFalse(result.passportType().isPresent());
    }

    @Test
    void extract_malformedJsonResponse_degradesToEmptyResult() {
        stubResponse("I'm not able to read this image clearly, sorry.");

        VisualPassportData result = adapter.extract(new byte[] {1, 2, 3});

        assertEquals(VisualPassportData.empty(), result);
    }

    @Test
    void extract_chatModelThrows_degradesToEmptyResult() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("upstream unavailable"));

        VisualPassportData result = adapter.extract(new byte[] {1, 2, 3});

        assertEquals(VisualPassportData.empty(), result);
    }

    /**
     * Guards against a regression where the image silently stops being sent
     * (e.g. someone "simplifies" the call to a text-only prompt): asserts the
     * {@link Prompt} handed to the chat model actually carries the raw image
     * bytes as {@link Media}, not just a text description of them. This is
     * what makes the call genuinely multimodal for any provider/model behind
     * {@code ChatModel} — including a local Ollama vision model.
     */
    @Test
    void extract_sendsImageBytesAsMultimodalMedia() {
        stubResponse("""
                {
                  "passportNumber": {"value": null, "readable": false},
                  "dateOfBirth": {"value": null, "readable": false},
                  "expiryDate": {"value": null, "readable": false},
                  "issueDate": {"value": null, "readable": false},
                  "placeOfBirth": {"value": null, "readable": false},
                  "passportType": {"value": null, "readable": false}
                }
                """);
        byte[] jpegBytes = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4, 5};

        adapter.extract(jpegBytes);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        UserMessage userMessage = (UserMessage) promptCaptor.getValue().getInstructions().get(0);
        List<Media> media = userMessage.getMedia();
        assertEquals(1, media.size());
        assertEquals("image/jpeg", media.get(0).getMimeType().toString());
        assertArrayEquals(jpegBytes, media.get(0).getDataAsByteArray());
    }

    @Test
    void extract_pngImage_detectsPngMimeType() {
        stubResponse("""
                {
                  "passportNumber": {"value": null, "readable": false},
                  "dateOfBirth": {"value": null, "readable": false},
                  "expiryDate": {"value": null, "readable": false},
                  "issueDate": {"value": null, "readable": false},
                  "placeOfBirth": {"value": null, "readable": false},
                  "passportType": {"value": null, "readable": false}
                }
                """);
        byte[] pngBytes = {(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0};

        adapter.extract(pngBytes);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        UserMessage userMessage = (UserMessage) promptCaptor.getValue().getInstructions().get(0);
        assertEquals("image/png", userMessage.getMedia().get(0).getMimeType().toString());
    }
}

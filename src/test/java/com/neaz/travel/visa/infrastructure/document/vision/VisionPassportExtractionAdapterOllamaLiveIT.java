package com.neaz.travel.visa.infrastructure.document.vision;

import com.neaz.travel.visa.domain.model.VisualFieldReading;
import com.neaz.travel.visa.domain.model.VisualPassportData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeTypeUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Live, opt-in verification that {@link VisionPassportExtractionAdapter} works
 * end-to-end against a real local Ollama instance running {@code llava:7b} —
 * the model configured for the {@code ollama} profile as of 2026-09-02,
 * replacing {@code gemma4:latest} (verified non-functional for this pipeline;
 * see {@code PROGRESS.md}'s 2026-09-01 entry).
 *
 * <p>Not run by default: requires a running local Ollama with {@code llava:7b}
 * pulled, which {@code mvn test} cannot assume. Run explicitly with:
 * <pre>mvn test -Dtest=VisionPassportExtractionAdapterOllamaLiveIT -Dlive-ollama=true</pre>
 *
 * <p>Uses a synthetically drawn bio-data page (not a real passport — no real
 * document image exists in this repo) with plainly legible printed text, so a
 * failure to read it is attributable to the model/pipeline, not image quality.
 */
@EnabledIfSystemProperty(named = "live-ollama", matches = "true")
class VisionPassportExtractionAdapterOllamaLiveIT {

    private static final String OLLAMA_BASE_URL = "http://localhost:11434";
    private static final String MODEL = "llava:7b";

    @Test
    void extract_realOllamaLlava_readsSyntheticPassportPage() throws IOException {
        byte[] imageBytes = renderSyntheticPassportPage();
        ChatModel chatModel = buildRealOllamaChatModel();

        // Stage 1: raw call bypassing the adapter's error-swallowing, so a
        // connection/model-not-found failure surfaces as a hard test failure
        // instead of silently looking like "the model just didn't read it."
        UserMessage rawProbe = UserMessage.builder()
                .text("Describe what you see in this image in one sentence.")
                .media(Media.builder()
                        .mimeType(MimeTypeUtils.IMAGE_PNG)
                        .data(new ByteArrayResource(imageBytes))
                        .build())
                .build();
        String rawDescription = chatModel.call(new Prompt(rawProbe)).getResult().getOutput().getText();
        System.out.println("[live-ollama] raw llava:7b image description: " + rawDescription);
        assertNotNull(rawDescription);
        assertFalse(rawDescription.isBlank(), "llava:7b returned an empty response to a direct image probe");

        // Stage 2: the real production adapter, against the same real model.
        VisionPassportExtractionAdapter adapter = new VisionPassportExtractionAdapter(chatModel);
        VisualPassportData result = adapter.extract(imageBytes);

        System.out.println("[live-ollama] passportNumber=" + describe(result.passportNumber()));
        System.out.println("[live-ollama] dateOfBirth=" + describe(result.dateOfBirth()));
        System.out.println("[live-ollama] expiryDate=" + describe(result.expiryDate()));
        System.out.println("[live-ollama] issueDate=" + describe(result.issueDate()));
        System.out.println("[live-ollama] placeOfBirth=" + describe(result.placeOfBirth()));
        System.out.println("[live-ollama] passportType=" + describe(result.passportType()));

        boolean atLeastOneFieldRead = result.passportNumber().isPresent()
                || result.dateOfBirth().isPresent()
                || result.expiryDate().isPresent()
                || result.issueDate().isPresent()
                || result.placeOfBirth().isPresent()
                || result.passportType().isPresent();

        // Deliberately not a hard assertion beyond "not silently empty": a
        // vision LLM's per-field OCR accuracy on a synthetic image is not
        // something to pin an assertion to without flaking. What this proves
        // is that the adapter's JSON contract round-tripped through a real
        // llava:7b response — i.e. the model both consumed the image AND
        // produced the expected JSON shape well enough to parse.
        if (!atLeastOneFieldRead) {
            throw new AssertionError("llava:7b responded (see raw probe above) but the adapter's structured "
                    + "extraction found no fields at all — either the JSON contract wasn't followed or the "
                    + "model couldn't read the synthetic page. Not a silent failure: see stdout for the raw "
                    + "model output logged by DocumentIntelligenceService-style callers, or add a temporary "
                    + "println in VisionPassportExtractionAdapter.parse() to inspect the raw JSON.");
        }
    }

    private static String describe(VisualFieldReading reading) {
        return reading.isPresent()
                ? "\"" + reading.rawValue() + "\" (clearlyReadable=" + reading.clearlyReadable() + ")"
                : "NOT_FOUND";
    }

    private static ChatModel buildRealOllamaChatModel() {
        OllamaApi ollamaApi = OllamaApi.builder().baseUrl(OLLAMA_BASE_URL).build();
        OllamaOptions options = OllamaOptions.builder().model(MODEL).temperature(0.1).build();
        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(options)
                .toolCallingManager(ToolCallingManager.builder().build())
                .build();
    }

    /** Draws a plain, high-contrast mock passport bio-data page — synthetic, not a real document. */
    private static byte[] renderSyntheticPassportPage() throws IOException {
        BufferedImage image = new BufferedImage(1000, 650, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 34));
        g.drawString("PASSPORT", 40, 60);
        g.setFont(new Font("Monospaced", Font.PLAIN, 30));
        int y = 140;
        int lineHeight = 55;
        for (String line : new String[] {
                "Type: P",
                "Passport No: A1234567",
                "Date of Birth: 15 MAY 1990",
                "Date of Issue: 15 MAY 2020",
                "Date of Expiry: 15 MAY 2030",
                "Place of Birth: DHAKA"
        }) {
            g.drawString(line, 40, y);
            y += lineHeight;
        }
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}

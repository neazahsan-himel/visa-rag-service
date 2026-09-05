package com.neaz.travel.visa.infrastructure.document.ocr;

import com.neaz.travel.visa.application.port.OcrPort;
import com.neaz.travel.visa.common.exception.VisaRagException;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * OCR adapter backed by a self-hosted Tesseract engine via Tess4J. Runs two
 * passes over an uploaded passport image: a full-page pass with Tesseract's
 * default English model, and a second pass restricted to the bottom band —
 * where a TD3 passport's machine-readable zone lives — using an MRZ-only
 * character whitelist with dictionary correction disabled.
 *
 * <p>The second pass exists because the default English model, tuned for
 * dictionary text, reliably misreads the MRZ's OCR-B filler character
 * ('&lt;') as stray letters even when every other field on the page reads
 * correctly — the general-purpose pass alone routinely fails {@code
 * MrzParser}'s checksum-validated exact-44-character match. Both passes'
 * output are concatenated so {@code MrzParser} can keep searching the
 * combined text for MRZ-format lines unchanged.
 *
 * <p>The MRZ band is also upscaled before this second pass. A crop taken
 * directly off a typical phone-camera passport photo is only ~30-40px tall
 * per line, and at that native resolution Tesseract's classifier frequently
 * mistakes the small OCR-B '&lt;' glyph for a letter — confirmed by feeding
 * the same crop straight to the Tesseract CLI with and without upscaling
 * (2026-09-02 investigation): the un-upscaled crop misread a filler run as
 * "AHSANS&lt;&lt;&lt;&lt;&lt;&lt;&lt;KKKKKKK", the same crop at 4x read
 * "AHSAN" correctly with only isolated noise left inside the padding run
 * (see {@code MrzParser} for how that residual noise is excluded). This is
 * a genuine resolution fix, not a guess.
 */
@Component
public class TesseractOcrAdapter implements OcrPort {

    private static final double MRZ_BAND_HEIGHT_FRACTION = 0.25;
    private static final int MRZ_BAND_UPSCALE_FACTOR = 4;
    private static final String MRZ_CHARSET_WHITELIST = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<";

    private final Tesseract fullPageTesseract;
    private final Tesseract mrzTesseract;

    public TesseractOcrAdapter(
            @Value("${ocr.tesseract.data-path}") String tessDataPath,
            @Value("${ocr.tesseract.language:eng}") String language) {
        this.fullPageTesseract = new Tesseract();
        this.fullPageTesseract.setDatapath(tessDataPath);
        this.fullPageTesseract.setLanguage(language);

        this.mrzTesseract = new Tesseract();
        this.mrzTesseract.setDatapath(tessDataPath);
        this.mrzTesseract.setLanguage(language);
        this.mrzTesseract.setPageSegMode(ITessAPI.TessPageSegMode.PSM_SINGLE_BLOCK);
        this.mrzTesseract.setVariable("tessedit_char_whitelist", MRZ_CHARSET_WHITELIST);
        this.mrzTesseract.setVariable("load_system_dawg", "0");
        this.mrzTesseract.setVariable("load_freq_dawg", "0");
    }

    @Override
    public String extractText(byte[] imageBytes) {
        BufferedImage image = decode(imageBytes);
        String fullPageText = doOcr(fullPageTesseract, image);
        String mrzBandText = doOcr(mrzTesseract, upscale(cropMrzBand(image), MRZ_BAND_UPSCALE_FACTOR));
        return fullPageText + "\n" + mrzBandText;
    }

    private static BufferedImage decode(byte[] imageBytes) {
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        } catch (IOException e) {
            throw new VisaRagException("Failed to decode uploaded image for OCR", e);
        }
        if (image == null) {
            throw new VisaRagException("Uploaded image could not be decoded for OCR");
        }
        return image;
    }

    private static BufferedImage cropMrzBand(BufferedImage image) {
        int height = image.getHeight();
        int bandHeight = Math.max(1, (int) Math.round(height * MRZ_BAND_HEIGHT_FRACTION));
        int bandStart = height - bandHeight;
        return image.getSubimage(0, bandStart, image.getWidth(), bandHeight);
    }

    private static BufferedImage upscale(BufferedImage source, int factor) {
        int targetWidth = source.getWidth() * factor;
        int targetHeight = source.getHeight() * factor;
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        g.dispose();
        return scaled;
    }

    private static String doOcr(Tesseract tesseract, BufferedImage image) {
        try {
            return tesseract.doOCR(image);
        } catch (TesseractException e) {
            throw new VisaRagException("OCR failed", e);
        }
    }
}

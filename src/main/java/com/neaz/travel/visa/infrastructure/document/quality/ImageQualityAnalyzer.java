package com.neaz.travel.visa.infrastructure.document.quality;

import com.neaz.travel.visa.application.port.ImageQualityPort;
import com.neaz.travel.visa.domain.model.ImageQualityAssessment;
import com.neaz.travel.visa.domain.model.ImageQualityIssue;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * Plain-Java (no OpenCV/native CV library) pre-OCR image-quality gate.
 * Every check here is a best-effort pixel-level heuristic, not a guarantee —
 * blur (Laplacian variance) and lighting (mean brightness) are reasonably
 * robust; glare/shadow (saturated-pixel proportion, no spatial clustering)
 * and skew (projection-profile angle search) are coarser approximations a
 * real computer-vision library would do more precisely. MRZ-band readability
 * assumes a roughly full bio-data-page framing and can misfire on a very
 * tightly cropped photo. These limits are accepted per the self-hosted,
 * no-external-OCR-API decision — the same honesty posture as Sprint 7's
 * "not automatically checkable" carve-out for dynamic portals.
 */
@Component
public class ImageQualityAnalyzer implements ImageQualityPort {

    private static final int ANALYSIS_MAX_DIMENSION = 400;

    private static final double BLUR_VARIANCE_THRESHOLD = 100.0;

    private static final double DARK_BRIGHTNESS_THRESHOLD = 60.0;
    private static final double BRIGHT_BRIGHTNESS_THRESHOLD = 200.0;

    private static final float GLARE_BRIGHTNESS = 250.0f;
    private static final float SHADOW_BRIGHTNESS = 8.0f;
    private static final double SATURATED_PIXEL_FRACTION_THRESHOLD = 0.08;

    private static final double SKEW_SEARCH_RANGE_DEGREES = 10.0;
    private static final double SKEW_SEARCH_STEP_DEGREES = 1.0;
    private static final double SKEW_ANGLE_THRESHOLD_DEGREES = 4.0;
    private static final double INK_PIXEL_BRIGHTNESS_RATIO = 0.75;

    private static final double MRZ_BAND_HEIGHT_FRACTION = 0.20;
    private static final double MRZ_BAND_EDGE_DENSITY_RATIO_THRESHOLD = 0.25;

    @Override
    public ImageQualityAssessment assess(byte[] imageBytes) {
        BufferedImage original = decode(imageBytes);
        if (original == null) {
            return ImageQualityAssessment.rejected(Set.of(ImageQualityIssue.MRZ_NOT_READABLE));
        }

        float[][] gray = toGrayscale(downscale(original, ANALYSIS_MAX_DIMENSION));
        Set<ImageQualityIssue> issues = EnumSet.noneOf(ImageQualityIssue.class);

        if (laplacianVariance(gray) < BLUR_VARIANCE_THRESHOLD) {
            issues.add(ImageQualityIssue.BLURRY);
        }
        double brightness = meanBrightness(gray);
        if (brightness < DARK_BRIGHTNESS_THRESHOLD || brightness > BRIGHT_BRIGHTNESS_THRESHOLD) {
            issues.add(ImageQualityIssue.POOR_LIGHTING);
        }
        if (saturatedPixelFraction(gray) > SATURATED_PIXEL_FRACTION_THRESHOLD) {
            issues.add(ImageQualityIssue.GLARE_OR_SHADOW);
        }
        if (Math.abs(estimateSkewDegrees(gray)) > SKEW_ANGLE_THRESHOLD_DEGREES) {
            issues.add(ImageQualityIssue.SKEWED);
        }
        if (!mrzBandReadable(gray)) {
            issues.add(ImageQualityIssue.MRZ_NOT_READABLE);
        }

        return issues.isEmpty() ? ImageQualityAssessment.accepted() : ImageQualityAssessment.rejected(issues);
    }

    private static BufferedImage decode(byte[] imageBytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(imageBytes));
        } catch (IOException e) {
            return null;
        }
    }

    private static BufferedImage downscale(BufferedImage source, int maxDimension) {
        int width = source.getWidth();
        int height = source.getHeight();
        double scale = Math.min(1.0, (double) maxDimension / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        g.dispose();
        return scaled;
    }

    private static float[][] toGrayscale(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        float[][] gray = new float[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                gray[y][x] = 0.299f * r + 0.587f * g + 0.114f * b;
            }
        }
        return gray;
    }

    private static double laplacianVariance(float[][] gray) {
        int height = gray.length;
        int width = gray[0].length;
        if (height < 3 || width < 3) {
            return 0.0;
        }
        double sum = 0.0;
        double sumSquares = 0.0;
        long count = 0;
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                float laplacian = -4 * gray[y][x] + gray[y - 1][x] + gray[y + 1][x] + gray[y][x - 1] + gray[y][x + 1];
                sum += laplacian;
                sumSquares += (double) laplacian * laplacian;
                count++;
            }
        }
        double mean = sum / count;
        return (sumSquares / count) - (mean * mean);
    }

    private static double meanBrightness(float[][] gray) {
        double sum = 0.0;
        long count = 0;
        for (float[] row : gray) {
            for (float value : row) {
                sum += value;
                count++;
            }
        }
        return sum / count;
    }

    private static double saturatedPixelFraction(float[][] gray) {
        long saturated = 0;
        long total = 0;
        for (float[] row : gray) {
            for (float value : row) {
                if (value >= GLARE_BRIGHTNESS || value <= SHADOW_BRIGHTNESS) {
                    saturated++;
                }
                total++;
            }
        }
        return (double) saturated / total;
    }

    /**
     * Estimates rotation via a projection-profile search: for each candidate
     * angle, project a binarized "ink" mask onto the rotated vertical axis
     * and bucket into rows. The angle whose row-count profile has the
     * highest variance (sharpest line separation) is taken as the skew.
     *
     * <p>A busy passport background (guilloché patterns, seals) can make
     * variance climb monotonically all the way to the edge of the search
     * range instead of peaking at the document's true angle — that's a
     * boundary artifact, not a measured skew, so it's reported as "no
     * reliable estimate" (0.0) rather than the boundary value itself.
     */
    private static double estimateSkewDegrees(float[][] gray) {
        double threshold = meanBrightness(gray) * INK_PIXEL_BRIGHTNESS_RATIO;
        boolean[][] ink = new boolean[gray.length][gray[0].length];
        for (int y = 0; y < gray.length; y++) {
            for (int x = 0; x < gray[0].length; x++) {
                ink[y][x] = gray[y][x] < threshold;
            }
        }

        double bestAngle = 0.0;
        double bestVariance = -1.0;
        for (double angle = -SKEW_SEARCH_RANGE_DEGREES; angle <= SKEW_SEARCH_RANGE_DEGREES; angle += SKEW_SEARCH_STEP_DEGREES) {
            double variance = rowProjectionVariance(ink, Math.toRadians(angle));
            if (variance > bestVariance) {
                bestVariance = variance;
                bestAngle = angle;
            }
        }
        if (Math.abs(bestAngle) >= SKEW_SEARCH_RANGE_DEGREES) {
            return 0.0;
        }
        return bestAngle;
    }

    private static double rowProjectionVariance(boolean[][] ink, double angleRadians) {
        int height = ink.length;
        int width = ink[0].length;
        double sin = Math.sin(angleRadians);
        double cos = Math.cos(angleRadians);

        double[] projection = new double[height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!ink[y][x]) {
                    continue;
                }
                int bucket = (int) Math.round(-x * sin + y * cos);
                if (bucket >= 0 && bucket < height) {
                    projection[bucket]++;
                }
            }
        }

        double mean = Arrays.stream(projection).average().orElse(0.0);
        double variance = 0.0;
        for (double value : projection) {
            variance += (value - mean) * (value - mean);
        }
        return variance / height;
    }

    private static boolean mrzBandReadable(float[][] gray) {
        int height = gray.length;
        int bandStart = (int) (height * (1 - MRZ_BAND_HEIGHT_FRACTION));
        double bandEdgeDensity = averageGradientMagnitude(gray, bandStart, height);
        double wholeImageEdgeDensity = averageGradientMagnitude(gray, 0, height);
        if (wholeImageEdgeDensity <= 0.0) {
            return false;
        }
        return (bandEdgeDensity / wholeImageEdgeDensity) >= MRZ_BAND_EDGE_DENSITY_RATIO_THRESHOLD;
    }

    private static double averageGradientMagnitude(float[][] gray, int rowStart, int rowEndExclusive) {
        int width = gray[0].length;
        double sum = 0.0;
        long count = 0;
        for (int y = Math.max(1, rowStart); y < Math.min(gray.length - 1, rowEndExclusive); y++) {
            for (int x = 1; x < width - 1; x++) {
                float gx = gray[y][x + 1] - gray[y][x - 1];
                float gy = gray[y + 1][x] - gray[y - 1][x];
                sum += Math.sqrt(gx * gx + gy * gy);
                count++;
            }
        }
        return count == 0 ? 0.0 : sum / count;
    }
}

package com.stegomsg.util;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates the built-in image library (ARCHITECTURE.md section 13) procedurally on
 * first run, so the project ships with zero binary assets and no external image
 * sourcing/licensing to worry about. Each image is a simple, visually distinct gradient
 * — purely a steganography carrier, not meant to look interesting on its own.
 *
 * All generated images are PNG (lossless — see ImageService/SteganographyService for why
 * that matters) at a range of sizes so the live capacity calculator has something
 * meaningful to demonstrate across "Safe" / "Warning" / "Too Large" states.
 */
public final class DefaultImageGenerator {

    private DefaultImageGenerator() {}

    public record Spec(String filename, int width, int height, Color from, Color to) {}

    private static final Spec[] SPECS = {
            new Spec("default_small.png", 320, 240, new Color(0x2b2f6e), new Color(0x6c5ce7)),
            new Spec("default_medium.png", 800, 600, new Color(0x1f6e5c), new Color(0x43c98d)),
            new Spec("default_large.png", 1920, 1080, new Color(0x6e3a1f), new Color(0xe0b64a)),
            new Spec("default_high_capacity.png", 2560, 1440, new Color(0x6e1f3a), new Color(0xe5556a)),
    };

    public static void ensureDefaultImagesExist(Path defaultImagesDir) {
        try {
            Files.createDirectories(defaultImagesDir);
            for (Spec spec : SPECS) {
                Path target = defaultImagesDir.resolve(spec.filename());
                if (!Files.exists(target)) {
                    BufferedImage image = render(spec);
                    ImageIO.write(image, "png", target.toFile());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static BufferedImage render(Spec spec) {
        BufferedImage image = new BufferedImage(spec.width(), spec.height(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        GradientPaint gradient = new GradientPaint(0, 0, spec.from(), spec.width(), spec.height(), spec.to());
        g.setPaint(gradient);
        g.fillRect(0, 0, spec.width(), spec.height());

        // Subtle diagonal texture so the carrier has some natural-looking variance in
        // its low bits, rather than perfectly smooth gradient bands.
        g.setColor(new Color(255, 255, 255, 18));
        for (int x = -spec.height(); x < spec.width(); x += 24) {
            g.drawLine(x, 0, x + spec.height(), spec.height());
        }
        g.dispose();
        return image;
    }
}

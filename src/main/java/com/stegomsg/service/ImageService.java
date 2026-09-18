package com.stegomsg.service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Loading, validating, and persisting images.
 *
 * SECURITY NOTE (ARCHITECTURE.md section 24 — treat uploaded images as untrusted input):
 *   - We never trust a file's extension or a client-supplied MIME type. The only thing
 *     that matters is whether ImageIO can actually decode the bytes as image data, and
 *     what pixel format results. {@link #loadAndValidate} is the single choke point
 *     every image — default library or user-uploaded — passes through before it is
 *     ever used as a steganography carrier or displayed.
 *   - Format choice: PNG only. PNG is lossless, so LSB modifications survive being
 *     saved/loaded exactly. JPEG's lossy DCT compression would destroy embedded bits on
 *     the very first re-save, silently corrupting messages — so JPEG input is rejected
 *     outright rather than accepted and quietly broken.
 *   - A maximum dimension/file-size cap avoids a maliciously huge "image" being used for
 *     a decompression-bomb-style resource exhaustion attack.
 */
public final class ImageService {

    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024; // 20 MB
    private static final int MAX_DIMENSION_PX = 6000;

    private final Path sentImagesDir;
    private final Path defaultImagesDir;

    public ImageService(Path sentImagesDir, Path defaultImagesDir) {
        this.sentImagesDir = sentImagesDir;
        this.defaultImagesDir = defaultImagesDir;
        try {
            Files.createDirectories(sentImagesDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public record DefaultImageOption(String name, String description, Path path) {
    }

    /** The built-in image library offered in the compose screen (section 13). */
    public List<DefaultImageOption> defaultImageLibrary() {
        return List.of(
                new DefaultImageOption("Small", "320\u00d7240 \u2014 low capacity, fast to send",
                        defaultImagesDir.resolve("default_small.png")),
                new DefaultImageOption("Medium", "800\u00d7600 \u2014 balanced capacity",
                        defaultImagesDir.resolve("default_medium.png")),
                new DefaultImageOption("Large", "1920\u00d71080 \u2014 high capacity",
                        defaultImagesDir.resolve("default_large.png")),
                new DefaultImageOption("High-capacity", "2560\u00d71440 \u2014 maximum built-in capacity",
                        defaultImagesDir.resolve("default_high_capacity.png"))
        );
    }

    /**
     * Loads and validates an image file as a steganography carrier. Throws
     * ImageValidationException with a safe, generic message on ANY problem — corrupted
     * file, disguised non-image, oversized file/dimensions, or wrong format. Never
     * surfaces the underlying decoder exception to the UI (section 35).
     */
    public BufferedImage loadAndValidate(Path file) {
        try {
            long size = Files.size(file);
            if (size == 0 || size > MAX_FILE_SIZE_BYTES) {
                throw new ImageValidationException("Selected image is empty or too large.");
            }
            if (!file.getFileName().toString().toLowerCase().endsWith(".png")) {
                throw new ImageValidationException("Only PNG images are supported (lossless format required).");
            }
            try (InputStream in = Files.newInputStream(file)) {
                BufferedImage image = ImageIO.read(in);
                if (image == null) {
                    throw new ImageValidationException("Unable to process this image.");
                }
                if (image.getWidth() > MAX_DIMENSION_PX || image.getHeight() > MAX_DIMENSION_PX) {
                    throw new ImageValidationException("Image dimensions exceed the supported maximum.");
                }
                return image;
            }
        } catch (IOException e) {
            throw new ImageValidationException("Unable to process this image.");
        }
    }

    /** Saves a stego-image (never overwrites; filenames are content-addressed by caller). */
    public Path saveSentImage(BufferedImage stegoImage, String filename) {
        Path out = sentImagesDir.resolve(filename);
        try {
            ImageIO.write(stegoImage, "png", out.toFile());
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public BufferedImage loadSentImage(Path path) {
        return loadAndValidate(path);
    }

    public static final class ImageValidationException extends RuntimeException {
        public ImageValidationException(String message) {
            super(message);
        }
    }
}

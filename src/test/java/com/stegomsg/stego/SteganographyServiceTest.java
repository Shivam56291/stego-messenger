package com.stegomsg.stego;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SteganographyServiceTest {

    private final SteganographyService stego = new SteganographyService();

    private BufferedImage randomImage(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(42);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, 0xFF000000 | (r.nextInt(256) << 16) | (r.nextInt(256) << 8) | r.nextInt(256));
            }
        }
        return img;
    }

    @Test
    void embedAndExtractRoundTrip() {
        BufferedImage carrier = randomImage(200, 150);
        byte[] payload = "this is a secret payload embedded in pixels".getBytes(StandardCharsets.UTF_8);

        BufferedImage stegoImage = stego.embed(carrier, payload, 1);
        byte[] extracted = stego.extract(stegoImage, 1);

        assertArrayEquals(payload, extracted);
    }

    @Test
    void embedDoesNotMutateOriginalImage() {
        BufferedImage carrier = randomImage(50, 50);
        int originalPixel = carrier.getRGB(0, 0);
        stego.embed(carrier, "hi".getBytes(StandardCharsets.UTF_8), 1);
        assertEquals(originalPixel, carrier.getRGB(0, 0), "embed() must not mutate the input image");
    }

    @Test
    void embedOnlyTouchesAsManyPixelsAsNeeded() {
        BufferedImage carrier = randomImage(200, 150);
        byte[] smallPayload = "tiny".getBytes(StandardCharsets.UTF_8);
        BufferedImage stegoImage = stego.embed(carrier, smallPayload, 1);

        int changed = 0;
        for (int y = 0; y < carrier.getHeight(); y++) {
            for (int x = 0; x < carrier.getWidth(); x++) {
                if (carrier.getRGB(x, y) != stegoImage.getRGB(x, y)) changed++;
            }
        }
        assertTrue(changed < carrier.getWidth() * carrier.getHeight(),
                "A tiny payload should not require modifying every pixel");
    }

    @Test
    void throwsWhenPayloadExceedsCapacity() {
        BufferedImage tinyCarrier = randomImage(4, 4); // capacity: 4*4*3*1/8 = 6 bytes
        byte[] tooBig = new byte[100];
        assertThrows(IllegalArgumentException.class, () -> stego.embed(tinyCarrier, tooBig, 1));
    }

    @Test
    void higherBitsPerChannelIncreasesCapacityAndStillRoundTrips() {
        BufferedImage carrier = randomImage(50, 50);
        byte[] payload = "payload using 2 bits per channel".getBytes(StandardCharsets.UTF_8);
        BufferedImage stegoImage = stego.embed(carrier, payload, 2);
        byte[] extracted = stego.extract(stegoImage, 2);
        assertArrayEquals(payload, extracted);
    }
}

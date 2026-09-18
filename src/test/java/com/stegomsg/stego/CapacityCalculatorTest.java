package com.stegomsg.stego;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CapacityCalculatorTest {

    private final CapacityCalculator calculator = new CapacityCalculator();

    @Test
    void maxPayloadMatchesHandComputedFormula() {
        // 100x100 image, 3 channels, 1 bit/channel => 100*100*3*1 bits = 30,000 bits = 3,750 bytes
        assertEquals(3750, calculator.maxPayloadBytes(100, 100, 1));
        // Doubling bits-per-channel doubles capacity.
        assertEquals(7500, calculator.maxPayloadBytes(100, 100, 2));
    }

    @Test
    void actualPayloadIncludesFixedOverhead() {
        long actual = calculator.actualPayloadBytes(50);
        assertEquals(50 + CapacityCalculator.FIXED_OVERHEAD_BYTES, actual);
    }

    @Test
    void largeImageSmallMessageIsSafe() {
        CapacityCalculator.CapacityReport report = calculator.report(1920, 1080, 1, 100);
        assertEquals(CapacityCalculator.Status.SAFE, report.status());
    }

    @Test
    void tinyImageBigMessageIsTooLarge() {
        CapacityCalculator.CapacityReport report = calculator.report(10, 10, 1, 5000);
        assertEquals(CapacityCalculator.Status.TOO_LARGE, report.status());
        assertTrue(report.actualPayloadBytes() > report.maxPayloadBytes());
    }

    @Test
    void nearCapacityIsFlaggedAsWarning() {
        // Choose an image whose capacity is just slightly above what a message needs (>=85%).
        long targetActual = 900;
        // max such that actual/max >= 0.85 but actual <= max
        // Solve for an image size close to this by trial: 3 channels, 1 bit/channel.
        int side = 55; // 55*55*3*1 bits / 8 = 1134 bytes max
        CapacityCalculator.CapacityReport report = calculator.report(side, side, 1,
                (int) (1134 * 0.9) - CapacityCalculator.FIXED_OVERHEAD_BYTES);
        assertTrue(report.percentUsed() >= 85.0 && report.percentUsed() <= 100.0);
        assertEquals(CapacityCalculator.Status.WARNING, report.status());
    }

    @Test
    void rejectsInvalidDimensions() {
        assertThrows(IllegalArgumentException.class, () -> calculator.maxPayloadBytes(0, 100, 1));
        assertThrows(IllegalArgumentException.class, () -> calculator.maxPayloadBytes(100, 100, 9));
    }
}

package com.stegomsg.util;

public final class Constants {
    private Constants() {}

    // --- Account lockout / rate limiting (see ARCHITECTURE.md section Z) ---
    public static final int MAX_LOGIN_ATTEMPTS_BEFORE_LOCKOUT = 5;
    public static final long LOGIN_LOCKOUT_SECONDS = 60; // doubled per repeat offense by AuthService
    public static final int MAX_PIN_ATTEMPTS_BEFORE_LOCKOUT = 5;
    public static final long PIN_LOCKOUT_SECONDS = 30;

    // --- Image history retention (section 19/38) ---
    public static final int MAX_IMAGE_HISTORY_ENTRIES = 10;

    // --- Steganography defaults (must match CapacityCalculator/SteganographyService) ---
    public static final int DEFAULT_BITS_PER_CHANNEL = 1;

    // --- Session ---
    public static final long SESSION_DURATION_SECONDS = 60L * 60 * 12; // 12 hours
}

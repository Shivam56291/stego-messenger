package com.stegomsg.util;

import java.util.regex.Pattern;

/** Input validation shared by the registration/login/settings screens and the service layer. */
public final class Validation {
    private Validation() {}

    // Intentionally simple/RFC-5322-lite — perfect email regexes are a well-known trap;
    // real verification happens via the email-confirmation link, not the regex.
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final int MIN_PASSWORD_LENGTH = 10;

    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    public static boolean isValidPin(String pin) {
        return pin != null && pin.matches("^\\d{6}$");
    }

    /** Returns a 0-4 strength score (used to drive the UI's strength meter) plus reasons. */
    public record PasswordStrength(int score, boolean meetsMinimum, String feedback) {}

    public static PasswordStrength assessPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            return new PasswordStrength(0, false, "Use at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        int score = 0;
        if (password.length() >= 12) score++;
        if (password.matches(".*[A-Z].*")) score++;
        if (password.matches(".*[a-z].*") && password.matches(".*[0-9].*")) score++;
        if (password.matches(".*[^A-Za-z0-9].*")) score++;
        String feedback = switch (score) {
            case 0, 1 -> "Weak — try adding length, symbols, and mixed case.";
            case 2 -> "Fair — a longer passphrase would be stronger.";
            case 3 -> "Good.";
            default -> "Strong.";
        };
        return new PasswordStrength(score, true, feedback);
    }
}

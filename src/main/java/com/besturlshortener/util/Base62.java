package com.besturlshortener.util;

/**
 * Base62 encoder/decoder for compact, collision-free short codes.
 * <p>
 * Uses a monotonic numeric ID mapped to [a-zA-Z0-9] — O(log₆₂ n) encode/decode,
 * fixed-length growth, and bijective mapping (no hash collisions).
 */
public final class Base62 {

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int BASE = ALPHABET.length();

    private Base62() {
    }

    public static String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Value must be non-negative");
        }
        if (value == 0) {
            return String.valueOf(ALPHABET.charAt(0));
        }

        StringBuilder encoded = new StringBuilder();
        long current = value;
        while (current > 0) {
            int remainder = (int) (current % BASE);
            encoded.append(ALPHABET.charAt(remainder));
            current /= BASE;
        }
        return encoded.reverse().toString();
    }

    public static long decode(String input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("Input must not be empty");
        }

        long result = 0;
        for (char character : input.toCharArray()) {
            int index = ALPHABET.indexOf(character);
            if (index < 0) {
                throw new IllegalArgumentException("Invalid Base62 character: " + character);
            }
            result = result * BASE + index;
        }
        return result;
    }
}

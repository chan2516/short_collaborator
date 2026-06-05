package com.besturlshortener.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Base62Test {

    @Test
    void encodeDecodeRoundTrip() {
        long[] values = {0, 1, 61, 62, 3843, 1_000_000, 56_800_235_514L};
        for (long value : values) {
            String encoded = Base62.encode(value);
            assertEquals(value, Base62.decode(encoded));
        }
    }

    @Test
    void knownEncodings() {
        assertEquals("a", Base62.encode(0));
        assertEquals("b", Base62.encode(1));
        assertEquals("ba", Base62.encode(62));
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> Base62.decode("abc!"));
    }
}

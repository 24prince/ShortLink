package com.shortlink.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class Base62CodecTest {

    private final Base62Codec codec = new Base62Codec();

    @Test
    void encodesKnownValues() {
        assertEquals("0", codec.encode(0));
        assertEquals("9", codec.encode(9));
        assertEquals("a", codec.encode(10));
        assertEquals("Z", codec.encode(61));
        assertEquals("10", codec.encode(62));
    }

    @Test
    void roundTripsLargeValues() {
        long[] samples = {1, 61, 62, 3843, 916_132_832L, 916_132_833L, 56_800_235_583L, Long.MAX_VALUE};
        for (long value : samples) {
            assertEquals(value, codec.decode(codec.encode(value)));
        }
    }

    @Test
    void generatedCodesStartAtSixCharacters() {
        // ID_OFFSET (62^5) guarantees even the first row gets a 6-character code.
        assertEquals(6, codec.encode(916_132_832L + 1).length());
        assertEquals("100001", codec.encode(916_132_832L + 1));
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> codec.encode(-1));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("ab!"));
    }
}

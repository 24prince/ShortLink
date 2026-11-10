package com.shortlink.service;

import org.springframework.stereotype.Component;

/**
 * Encodes non-negative numbers using the alphabet 0-9, a-z, A-Z.
 * Six characters cover 62^6 (~56.8 billion) distinct values.
 */
@Component
public class Base62Codec {

    private static final String ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = ALPHABET.length();

    public String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be non-negative");
        }
        if (value == 0) {
            return String.valueOf(ALPHABET.charAt(0));
        }
        StringBuilder sb = new StringBuilder();
        long n = value;
        while (n > 0) {
            sb.append(ALPHABET.charAt((int) (n % BASE)));
            n /= BASE;
        }
        return sb.reverse().toString();
    }

    public long decode(String code) {
        long result = 0;
        for (char c : code.toCharArray()) {
            int digit = ALPHABET.indexOf(c);
            if (digit < 0) {
                throw new IllegalArgumentException("invalid Base62 character: " + c);
            }
            result = result * BASE + digit;
        }
        return result;
    }
}

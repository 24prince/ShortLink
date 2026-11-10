package com.shortlink.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.shortlink.exception.InvalidUrlException;

class UrlValidatorTest {

    private final UrlValidator validator = new UrlValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com",
            "http://example.com/path?q=1#frag",
            "HTTPS://Example.com/Case",
            "https://sub.example.co.in:8443/a/b"})
    void acceptsHttpAndHttps(String url) {
        assertEquals(url, validator.normalize(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "data:text/html;base64,PHNjcmlwdD4=",
            "file:///etc/passwd",
            "ftp://example.com/file",
            "example.com",
            "//example.com",
            "https://",
            "http://exa mple.com",
            "   "})
    void rejectsEverythingElse(String url) {
        assertThrows(InvalidUrlException.class, () -> validator.normalize(url));
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertEquals("https://example.com", validator.normalize("  https://example.com  "));
    }
}

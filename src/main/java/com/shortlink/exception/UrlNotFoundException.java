package com.shortlink.exception;

public class UrlNotFoundException extends RuntimeException {

    public UrlNotFoundException(String code) {
        super(String.format("No short link found for code '%s'", code));
    }
}

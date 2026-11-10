package com.shortlink.exception;

public class LinkExpiredException extends RuntimeException {

    public LinkExpiredException(String code) {
        super(String.format("Short link '%s' has expired", code));
    }
}

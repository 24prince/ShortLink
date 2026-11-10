package com.shortlink.exception;

public class AliasUnavailableException extends RuntimeException {

    public AliasUnavailableException(String code) {
        super(String.format("Alias '%s' is not available", code));
    }
}

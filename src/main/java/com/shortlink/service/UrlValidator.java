package com.shortlink.service;

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.stereotype.Component;

import com.shortlink.exception.InvalidUrlException;

/**
 * Only absolute http(s) URLs with a host are accepted. Rejecting everything else
 * (javascript:, data:, file:, ftp: ...) prevents the shortener from being used
 * to serve dangerous redirect targets.
 */
@Component
public class UrlValidator {

    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidUrlException("url must not be blank");
        }
        String candidate = raw.trim();

        URI uri;
        try {
            uri = new URI(candidate);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("url is not a valid URI");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new InvalidUrlException("only http and https URLs are supported");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidUrlException("url must contain a host");
        }
        return candidate;
    }
}

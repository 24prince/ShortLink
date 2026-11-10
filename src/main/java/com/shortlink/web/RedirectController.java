package com.shortlink.web;

import java.net.URI;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.shortlink.exception.UrlNotFoundException;
import com.shortlink.service.ShortenerService;

@RestController
public class RedirectController {

    private static final Pattern VALID_CODE = Pattern.compile("^[A-Za-z0-9_-]{4,30}$");

    private final ShortenerService service;

    public RedirectController(ShortenerService service) {
        this.service = service;
    }

    /**
     * 302 (not 301) on purpose: browsers cache a 301 permanently and would never hit the
     * server again, which would break click counting and make expiry/deletion ineffective.
     */
    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        if (!VALID_CODE.matcher(code).matches()) {
            // Cheap rejection: malformed codes never touch Redis or MySQL.
            throw new UrlNotFoundException(code);
        }
        String target = service.resolve(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target))
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .build();
    }
}

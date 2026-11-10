package com.shortlink.web;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shortlink.config.ShortLinkProperties;
import com.shortlink.domain.ShortUrl;
import com.shortlink.dto.CreateUrlRequest;
import com.shortlink.dto.CreateUrlResponse;
import com.shortlink.dto.UrlStatsResponse;
import com.shortlink.service.ShortenerService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/urls")
public class UrlController {

    private final ShortenerService service;
    private final String baseUrl;

    public UrlController(ShortenerService service, ShortLinkProperties props) {
        this.service = service;
        String base = props.baseUrl();
        this.baseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    @PostMapping
    public ResponseEntity<CreateUrlResponse> create(@Valid @RequestBody CreateUrlRequest request) {
        ShortUrl created = service.create(request);
        String shortUrl = baseUrl + "/" + created.getShortCode();
        CreateUrlResponse body = new CreateUrlResponse(
                created.getShortCode(), shortUrl, created.getOriginalUrl(),
                created.getCreatedAt(), created.getExpiresAt());
        return ResponseEntity.created(URI.create(shortUrl)).body(body);
    }

    @GetMapping("/{code}/stats")
    public UrlStatsResponse stats(@PathVariable String code) {
        return service.stats(code);
    }
}

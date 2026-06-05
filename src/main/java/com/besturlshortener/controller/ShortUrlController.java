package com.besturlshortener.controller;

import com.besturlshortener.dto.ShortenRequest;
import com.besturlshortener.dto.ShortenResponse;
import com.besturlshortener.dto.UrlStatsResponse;
import com.besturlshortener.service.UrlShorteningService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ShortUrlController {

    private final UrlShorteningService urlShorteningService;

    public ShortUrlController(UrlShorteningService urlShorteningService) {
        this.urlShorteningService = urlShorteningService;
    }

    @PostMapping("/shorten")
    @ResponseStatus(HttpStatus.CREATED)
    public ShortenResponse shorten(@Valid @RequestBody ShortenRequest request) {
        return urlShorteningService.shorten(request.url());
    }

    @GetMapping("/stats/{shortCode}")
    public UrlStatsResponse stats(@PathVariable String shortCode) {
        return urlShorteningService.getStats(shortCode);
    }
}

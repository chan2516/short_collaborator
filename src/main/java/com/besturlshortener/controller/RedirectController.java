package com.besturlshortener.controller;

import com.besturlshortener.service.UrlShorteningService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class RedirectController {

    private final UrlShorteningService urlShorteningService;

    public RedirectController(UrlShorteningService urlShorteningService) {
        this.urlShorteningService = urlShorteningService;
    }

    @GetMapping("/{shortCode:[a-zA-Z0-9]+}")
    public String redirect(@PathVariable String shortCode) {
        String originalUrl = urlShorteningService.resolveOriginalUrl(shortCode);
        urlShorteningService.recordClick(shortCode);
        return "redirect:" + originalUrl;
    }
}

package com.besturlshortener.service;

import com.besturlshortener.config.AppProperties;
import com.besturlshortener.config.CacheConfig;
import com.besturlshortener.dto.ShortenResponse;
import com.besturlshortener.dto.UrlStatsResponse;
import com.besturlshortener.entity.UrlMapping;
import com.besturlshortener.exception.UrlNotFoundException;
import com.besturlshortener.repository.UrlMappingRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class UrlShorteningService {

    private final UrlMappingRepository repository;
    private final AppProperties appProperties;

    public UrlShorteningService(UrlMappingRepository repository, AppProperties appProperties) {
        this.repository = repository;
        this.appProperties = appProperties;
    }

    /**
     * Shorten a URL using auto-increment ID + Base62 encoding.
     * Bijective mapping: no collisions, O(1) lookup by short code (indexed).
     * Duplicate URLs are deduplicated via SHA-256 hash — O(1) average.
     */
    @Transactional
    public ShortenResponse shorten(String originalUrl) {
        String normalizedUrl = normalizeUrl(originalUrl);
        String urlHash = hashUrl(normalizedUrl);

        return repository.findByOriginalUrlHash(urlHash)
                .map(this::toShortenResponse)
                .orElseGet(() -> createNewMapping(normalizedUrl, urlHash));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.URL_CACHE, key = "#shortCode")
    public String resolveOriginalUrl(String shortCode) {
        UrlMapping mapping = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        if (mapping.getExpiresAt() != null && mapping.getExpiresAt().isBefore(java.time.Instant.now())) {
            throw new UrlNotFoundException(shortCode);
        }

        return mapping.getOriginalUrl();
    }

    @Transactional
    public void recordClick(String shortCode) {
        repository.incrementClickCount(shortCode);
    }

    @Transactional(readOnly = true)
    public UrlStatsResponse getStats(String shortCode) {
        UrlMapping mapping = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        return new UrlStatsResponse(
                mapping.getShortCode(),
                buildShortUrl(mapping.getShortCode()),
                mapping.getOriginalUrl(),
                mapping.getClickCount(),
                mapping.getCreatedAt()
        );
    }

    private ShortenResponse createNewMapping(String normalizedUrl, String urlHash) {
        UrlMapping mapping = repository.save(new UrlMapping(normalizedUrl, urlHash));
        mapping.assignShortCodeFromId();
        mapping = repository.save(mapping);
        return toShortenResponse(mapping);
    }

    private ShortenResponse toShortenResponse(UrlMapping mapping) {
        return new ShortenResponse(
                mapping.getShortCode(),
                buildShortUrl(mapping.getShortCode()),
                mapping.getOriginalUrl(),
                mapping.getCreatedAt()
        );
    }

    private String buildShortUrl(String shortCode) {
        return appProperties.getBaseUrl() + "/" + shortCode;
    }

    private String normalizeUrl(String url) {
        return url.trim();
    }

    private String hashUrl(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

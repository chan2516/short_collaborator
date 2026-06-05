package com.besturlshortener.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.besturlshortener.util.Base62;

import java.time.Instant;

@Entity
@Table(
        name = "url_mappings",
        uniqueConstraints = @UniqueConstraint(name = "uk_short_code", columnNames = "short_code"),
        indexes = {
                @Index(name = "idx_short_code", columnList = "short_code"),
                @Index(name = "idx_original_url_hash", columnList = "original_url_hash")
        }
)
public class UrlMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", length = 12)
    private String shortCode;

    @Column(name = "original_url", nullable = false, length = 2048)
    private String originalUrl;

    @Column(name = "original_url_hash", nullable = false, length = 64)
    private String originalUrlHash;

    @Column(name = "click_count", nullable = false)
    private long clickCount = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected UrlMapping() {
    }

    public UrlMapping(String originalUrl, String originalUrlHash) {
        this.originalUrl = originalUrl;
        this.originalUrlHash = originalUrlHash;
    }

    public void assignShortCodeFromId() {
        if (this.id == null) {
            throw new IllegalStateException("Cannot assign short code before ID is generated");
        }
        this.shortCode = Base62.encode(this.id);
    }

    public Long getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public String getOriginalUrlHash() {
        return originalUrlHash;
    }

    public long getClickCount() {
        return clickCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void incrementClickCount() {
        this.clickCount++;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}

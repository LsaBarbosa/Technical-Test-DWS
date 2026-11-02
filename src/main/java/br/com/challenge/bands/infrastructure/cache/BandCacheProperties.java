package br.com.challenge.bands.infrastructure.cache;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "bands.cache")
public record BandCacheProperties(@NotNull Duration ttl, @Positive long maximumSize) {}

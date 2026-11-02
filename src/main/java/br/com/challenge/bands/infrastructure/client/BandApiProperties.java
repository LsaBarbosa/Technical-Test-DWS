package br.com.challenge.bands.infrastructure.client;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bands.api")
public record BandApiProperties(URI url, Duration connectTimeout, Duration readTimeout) {}

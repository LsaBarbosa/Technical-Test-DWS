package br.com.challenge.bands.infrastructure.client;

import br.com.challenge.bands.domain.model.Band;
import br.com.challenge.bands.domain.port.BandCatalogPort;
import br.com.challenge.bands.infrastructure.client.dto.ExternalBandDto;
import br.com.challenge.bands.infrastructure.client.exception.ExternalServiceException;
import br.com.challenge.bands.infrastructure.client.exception.InvalidExternalResponseException;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.stereotype.Component;

@Component
public class BandApiClient implements BandCatalogPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(BandApiClient.class);
    private static final ParameterizedTypeReference<List<ExternalBandDto>> BAND_LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    public BandApiClient(RestClient bandApiRestClient) {
        this.restClient = bandApiRestClient;
    }

    @Override
    public List<Band> findAll() {
        long startedAt = System.nanoTime();
        final List<ExternalBandDto> externalBands;
        LOGGER.info("Requesting band catalog from external service");
        try {
            externalBands = restClient.get()
                    .retrieve()
                    .body(BAND_LIST_TYPE);
        } catch (ResourceAccessException exception) {
            LOGGER.warn(
                    "Communication with external band service failed after {} ms ({})",
                    elapsedMillis(startedAt),
                    rootCauseType(exception));
            throw new ExternalServiceException("Failed to retrieve bands from external service", exception);
        } catch (RestClientResponseException exception) {
            LOGGER.warn(
                    "External band service returned HTTP {} after {} ms",
                    exception.getStatusCode().value(),
                    elapsedMillis(startedAt));
            throw new ExternalServiceException("Failed to retrieve bands from external service", exception);
        } catch (HttpMessageConversionException exception) {
            LOGGER.warn("External band service returned an unreadable response after {} ms", elapsedMillis(startedAt));
            throw new InvalidExternalResponseException("External service returned an invalid response", exception);
        } catch (RestClientException exception) {
            if (causedByMessageConversion(exception)) {
                LOGGER.warn("External band service returned an unreadable response after {} ms", elapsedMillis(startedAt));
                throw new InvalidExternalResponseException("External service returned an invalid response", exception);
            }
            LOGGER.warn(
                    "Communication with external band service failed after {} ms ({})",
                    elapsedMillis(startedAt),
                    rootCauseType(exception));
            throw new ExternalServiceException("Failed to retrieve bands from external service", exception);
        }

        if (externalBands == null) {
            LOGGER.warn("External band service returned an empty response body after {} ms", elapsedMillis(startedAt));
            throw invalidResponse("Response body must be a JSON array");
        }

        final List<Band> bands;
        try {
            bands = externalBands.stream().map(this::toDomain).toList();
        } catch (IllegalArgumentException | NullPointerException exception) {
            LOGGER.warn(
                    "External band service response did not satisfy the required band fields ({} ms)",
                    elapsedMillis(startedAt));
            throw new InvalidExternalResponseException("External service returned invalid band data", exception);
        }

        LOGGER.info("Received {} bands from external service in {} ms", bands.size(), elapsedMillis(startedAt));
        return bands;
    }

    private Band toDomain(ExternalBandDto dto) {
        Objects.requireNonNull(dto, "band item must not be null");
        Objects.requireNonNull(dto.numPlays(), "numPlays must not be null");
        Objects.requireNonNull(dto.albums(), "albums must not be null");

        return new Band(
                dto.id(),
                dto.name(),
                dto.image(),
                dto.genre(),
                dto.biography(),
                dto.numPlays(),
                dto.albums());
    }

    private InvalidExternalResponseException invalidResponse(String message) {
        return new InvalidExternalResponseException(message, new IllegalArgumentException(message));
    }

    private boolean causedByMessageConversion(Throwable exception) {
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpMessageConversionException) {
                return true;
            }
        }
        return false;
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private String rootCauseType(Throwable exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        return rootCause.getClass().getSimpleName();
    }
}

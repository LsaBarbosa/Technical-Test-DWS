package br.com.challenge.bands.web.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.challenge.bands.application.service.BandNotFoundException;
import br.com.challenge.bands.application.service.BandService;
import br.com.challenge.bands.application.service.BandSortField;
import br.com.challenge.bands.application.service.SortDirection;
import br.com.challenge.bands.domain.model.Band;
import br.com.challenge.bands.infrastructure.client.exception.ExternalServiceException;
import br.com.challenge.bands.infrastructure.client.exception.InvalidExternalResponseException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BandController.class)
class BandControllerTest {

    private static final UUID BAND_ID = UUID.fromString("bc710bcf-8815-42cf-bad2-3f1d12246aeb");
    private static final UUID ALBUM_ID = UUID.fromString("3c5794a0-d913-390d-ab24-6762af38c112");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BandService bandService;

    @Test
    void shouldReturnBandSummariesWithDefaultSortAndCorrectJsonFields() throws Exception {
        when(bandService.findBands(null, BandSortField.NAME, SortDirection.ASC)).thenReturn(List.of(band()));

        mockMvc.perform(get("/api/bands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(BAND_ID.toString()))
                .andExpect(jsonPath("$[0].name").value("Nickelback"))
                .andExpect(jsonPath("$[0].image").value("https://example.com/band.png"))
                .andExpect(jsonPath("$[0].numPlays").value(17605491))
                .andExpect(jsonPath("$[0].genre").doesNotExist())
                .andExpect(contentTypeJson());

        verify(bandService).findBands(null, BandSortField.NAME, SortDirection.ASC);
    }

    @Test
    void shouldPassQueryAndSortParametersToService() throws Exception {
        when(bandService.findBands("nick", BandSortField.NUM_PLAYS, SortDirection.DESC)).thenReturn(List.of(band()));

        mockMvc.perform(get("/api/bands")
                        .param("query", "nick")
                        .param("sortBy", "numPlays")
                        .param("direction", "desc"))
                .andExpect(status().isOk());

        verify(bandService).findBands("nick", BandSortField.NUM_PLAYS, SortDirection.DESC);
    }

    @Test
    void shouldReturnBandDetails() throws Exception {
        when(bandService.findById(BAND_ID)).thenReturn(band());

        mockMvc.perform(get("/api/bands/{id}", BAND_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(BAND_ID.toString()))
                .andExpect(jsonPath("$.name").value("Nickelback"))
                .andExpect(jsonPath("$.image").value("https://example.com/band.png"))
                .andExpect(jsonPath("$.genre").value("rock"))
                .andExpect(jsonPath("$.biography").value("Biography"))
                .andExpect(jsonPath("$.numPlays").value(17605491))
                .andExpect(jsonPath("$.albums[0]").value(ALBUM_ID.toString()));
    }

    @Test
    void shouldReturn404WhenBandDoesNotExist() throws Exception {
        when(bandService.findById(BAND_ID)).thenThrow(new BandNotFoundException(BAND_ID));

        mockMvc.perform(get("/api/bands/{id}", BAND_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Band not found: " + BAND_ID))
                .andExpect(jsonPath("$.path").value("/api/bands/" + BAND_ID))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturn400ForInvalidUuid() throws Exception {
        mockMvc.perform(get("/api/bands/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'id'"));
    }

    @Test
    void shouldReturn400ForUnknownSortField() throws Exception {
        mockMvc.perform(get("/api/bands").param("sortBy", "genre"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("sortBy must be 'name' or 'numPlays'"));
    }

    @Test
    void shouldReturn400ForUnknownDirection() throws Exception {
        mockMvc.perform(get("/api/bands").param("direction", "sideways"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("direction must be 'asc' or 'desc'"));
    }

    @Test
    void shouldReturn503ForUpstreamCommunicationFailure() throws Exception {
        when(bandService.findBands(isNull(), eq(BandSortField.NAME), eq(SortDirection.ASC)))
                .thenThrow(new ExternalServiceException("upstream failed", new ConnectException()));

        mockMvc.perform(get("/api/bands"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("External service is unavailable"));
    }

    @Test
    void shouldReturn504WhenUpstreamTimesOut() throws Exception {
        when(bandService.findBands(isNull(), eq(BandSortField.NAME), eq(SortDirection.ASC)))
                .thenThrow(new ExternalServiceException("upstream timed out", new SocketTimeoutException()));

        mockMvc.perform(get("/api/bands"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.message").value("External service timed out"));
    }

    @Test
    void shouldReturn502ForInvalidUpstreamResponse() throws Exception {
        when(bandService.findBands(isNull(), eq(BandSortField.NAME), eq(SortDirection.ASC)))
                .thenThrow(new InvalidExternalResponseException("invalid upstream", new IllegalArgumentException()));

        mockMvc.perform(get("/api/bands"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("External service returned an invalid response"));
    }

    private static Band band() {
        return new Band(BAND_ID, "Nickelback", "https://example.com/band.png", "rock", "Biography", 17605491, List.of(ALBUM_ID));
    }

    private static org.springframework.test.web.servlet.ResultMatcher contentTypeJson() {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON);
    }
}

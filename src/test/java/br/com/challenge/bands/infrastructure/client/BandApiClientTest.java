package br.com.challenge.bands.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import br.com.challenge.bands.domain.model.Band;
import br.com.challenge.bands.infrastructure.client.exception.ExternalServiceException;
import br.com.challenge.bands.infrastructure.client.exception.InvalidExternalResponseException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class BandApiClientTest {

    private static final String API_URL = "https://bands-api.test/api/bands";
    private static final UUID BAND_ID = UUID.fromString("bc710bcf-8815-42cf-bad2-3f1d12246aeb");
    private static final UUID ALBUM_ID = UUID.fromString("3c5794a0-d913-390d-ab24-6762af38c112");

    private MockRestServiceServer server;
    private BandApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new BandApiClient(builder.baseUrl(API_URL).build());
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void shouldDeserializeAndMapBandsAndReturnImmutableList() {
        expectJson("""
                [{"id":"bc710bcf-8815-42cf-bad2-3f1d12246aeb","name":"Nickelback",
                  "image":"https://example.com/band.png","genre":"rock",
                  "biography":"Biography","numPlays":17605491,
                  "albums":["3c5794a0-d913-390d-ab24-6762af38c112"]}]
                """);

        List<Band> bands = client.findAll();

        assertEquals(1, bands.size());
        Band band = bands.getFirst();
        assertEquals(BAND_ID, band.id());
        assertEquals("Nickelback", band.name());
        assertEquals("https://example.com/band.png", band.image());
        assertEquals("rock", band.genre());
        assertEquals("Biography", band.biography());
        assertEquals(17605491L, band.numPlays());
        assertEquals(List.of(ALBUM_ID), band.albumIds());
        assertThrows(UnsupportedOperationException.class, () -> bands.add(band));
    }

    @Test
    void shouldAcceptAnEmptyArray() {
        expectJson("[]");

        assertTrue(client.findAll().isEmpty());
    }

    @Test
    void shouldRejectMissingId() {
        expectJson("[{\"name\":\"Band\",\"numPlays\":1,\"albums\":[]}]");

        assertInvalidResponse();
    }

    @Test
    void shouldRejectMissingName() {
        expectJson("[{\"id\":\"bc710bcf-8815-42cf-bad2-3f1d12246aeb\",\"numPlays\":1,\"albums\":[]}]");

        assertInvalidResponse();
    }

    @Test
    void shouldRejectMissingNumPlays() {
        expectJson("[{\"id\":\"bc710bcf-8815-42cf-bad2-3f1d12246aeb\",\"name\":\"Band\",\"albums\":[]}]");

        assertInvalidResponse();
    }

    @Test
    void shouldRejectNegativeNumPlays() {
        expectJson("[{\"id\":\"bc710bcf-8815-42cf-bad2-3f1d12246aeb\",\"name\":\"Band\",\"numPlays\":-1,\"albums\":[]}]");

        assertInvalidResponse();
    }

    @Test
    void shouldRejectNullAlbums() {
        expectJson("[{\"id\":\"bc710bcf-8815-42cf-bad2-3f1d12246aeb\",\"name\":\"Band\",\"numPlays\":1,\"albums\":null}]");

        assertInvalidResponse();
    }

    @Test
    void shouldRejectInvalidJson() {
        expectJson("{invalid-json");

        assertThrows(InvalidExternalResponseException.class, client::findAll);
    }

    @Test
    void shouldRejectAnEmptyResponseBody() {
        expectJson("");

        assertInvalidResponse();
    }

    @Test
    void shouldTranslateHttpServerErrorAndPreserveCause() {
        server.expect(requestTo(API_URL)).andExpect(method(HttpMethod.GET)).andRespond(withServerError());

        ExternalServiceException exception = assertThrows(ExternalServiceException.class, client::findAll);

        assertNotNull(exception.getCause());
    }

    @Test
    void shouldTranslateHttpClientErrorAndPreserveCause() {
        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        ExternalServiceException exception = assertThrows(ExternalServiceException.class, client::findAll);

        assertNotNull(exception.getCause());
    }

    @Test
    void shouldTranslateConnectionFailureAndPreserveCause() {
        ResourceAccessException cause = new ResourceAccessException("Connection refused", new ConnectException());
        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> {
                    throw cause;
                });

        ExternalServiceException exception = assertThrows(ExternalServiceException.class, client::findAll);

        assertEquals(cause, exception.getCause());
    }

    @Test
    void shouldTranslateDnsFailureAndPreserveCause() {
        ResourceAccessException cause = new ResourceAccessException("DNS lookup failed", new UnknownHostException());
        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> {
                    throw cause;
                });

        ExternalServiceException exception = assertThrows(ExternalServiceException.class, client::findAll);

        assertEquals(cause, exception.getCause());
    }

    @Test
    void shouldTranslateConnectionTimeoutAndPreserveCause() {
        ResourceAccessException cause = new ResourceAccessException(
                "Connection timed out", new HttpConnectTimeoutException("Connection timed out"));
        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> {
                    throw cause;
                });

        ExternalServiceException exception = assertThrows(ExternalServiceException.class, client::findAll);

        assertEquals(cause, exception.getCause());
    }

    @Test
    void shouldTranslateTimeoutAndPreserveCause() {
        ResourceAccessException cause = new ResourceAccessException("Read timed out", new SocketTimeoutException());
        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> {
                    throw cause;
                });

        ExternalServiceException exception = assertThrows(ExternalServiceException.class, client::findAll);

        assertEquals(cause, exception.getCause());
    }

    private void expectJson(String json) {
        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private void assertInvalidResponse() {
        InvalidExternalResponseException exception =
                assertThrows(InvalidExternalResponseException.class, client::findAll);
        assertNotNull(exception.getCause());
    }
}

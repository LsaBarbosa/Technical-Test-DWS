package br.com.challenge.bands.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BandsEndToEndTest {

    private static final String NICKELBACK_ID = "e2c00c56-8365-4160-9f40-a64682917633";
    private static final String ALBUM_ID = "3c5794a0-d913-390d-ab24-6762af38c112";
    private static final String CATALOG_JSON = """
            [
              {"id":"bc710bcf-8815-42cf-bad2-3f1d12246aeb","name":"Zulu","image":"https://example.test/zulu.png","genre":"rock","biography":"Zulu biography","numPlays":10,"albums":[]},
              {"id":"e2c00c56-8365-4160-9f40-a64682917633","name":"Nickelback","image":"https://example.test/nickelback.png","genre":"rock","biography":"Nickelback biography","numPlays":200,"albums":["3c5794a0-d913-390d-ab24-6762af38c112"]},
              {"id":"a3cb23fc-acd3-4ce0-8f36-1e5aa6a18432","name":"Abba","image":"https://example.test/abba.png","genre":"pop","biography":"Abba biography","numPlays":100,"albums":[]}
            ]
            """;
    private static final AtomicInteger EXTERNAL_CALLS = new AtomicInteger();
    private static final AtomicReference<UpstreamResponse> UPSTREAM_RESPONSE =
            new AtomicReference<>(new UpstreamResponse(200, CATALOG_JSON, 0));
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private static HttpServer externalServer;
    private static ExecutorService externalExecutor;

    @LocalServerPort
    private int applicationPort;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CacheManager cacheManager;

    @DynamicPropertySource
    static void configureApplication(DynamicPropertyRegistry registry) {
        registry.add("bands.api.url", BandsEndToEndTest::externalApiUrl);
        registry.add("bands.api.connect-timeout", () -> "1s");
        registry.add("bands.api.read-timeout", () -> "250ms");
        registry.add("bands.cache.ttl", () -> "5m");
    }

    @BeforeEach
    void resetState() {
        EXTERNAL_CALLS.set(0);
        UPSTREAM_RESPONSE.set(new UpstreamResponse(200, CATALOG_JSON, 0));
        cacheManager.getCache("bandsCatalog").clear();
    }

    @AfterAll
    static void stopExternalServer() {
        if (externalServer != null) {
            externalServer.stop(0);
        }
        if (externalExecutor != null) {
            externalExecutor.shutdownNow();
        }
    }

    @Test
    void shouldServeListSearchSortingAndDetailsThroughTheExternalAdapter() throws Exception {
        HttpResponse<String> listResponse = get("/api/bands");
        assertEquals(200, listResponse.statusCode());
        JsonNode list = objectMapper.readTree(listResponse.body());
        assertEquals(3, list.size());
        assertEquals("Abba", list.get(0).get("name").asText());
        assertEquals(Set.of("id", "name", "image", "numPlays"), fieldNames(list.get(0)));

        HttpResponse<String> searchResponse = get("/api/bands?query=nickelback");
        assertEquals(200, searchResponse.statusCode());
        JsonNode search = objectMapper.readTree(searchResponse.body());
        assertEquals(1, search.size());
        assertEquals("Nickelback", search.get(0).get("name").asText());

        HttpResponse<String> alphabeticalResponse = get("/api/bands?sortBy=name&direction=asc");
        assertEquals(200, alphabeticalResponse.statusCode());
        JsonNode alphabetical = objectMapper.readTree(alphabeticalResponse.body());
        assertEquals("Abba", alphabetical.get(0).get("name").asText());
        assertEquals("Nickelback", alphabetical.get(1).get("name").asText());
        assertEquals("Zulu", alphabetical.get(2).get("name").asText());

        HttpResponse<String> popularResponse = get("/api/bands?sortBy=numPlays&direction=desc");
        assertEquals(200, popularResponse.statusCode());
        JsonNode popular = objectMapper.readTree(popularResponse.body());
        assertEquals("Nickelback", popular.get(0).get("name").asText());
        assertEquals(200, popular.get(0).get("numPlays").asLong());

        HttpResponse<String> detailResponse = get("/api/bands/" + NICKELBACK_ID);
        assertEquals(200, detailResponse.statusCode());
        JsonNode detail = objectMapper.readTree(detailResponse.body());
        assertEquals(NICKELBACK_ID, detail.get("id").asText());
        assertEquals(Set.of("id", "name", "image", "genre", "biography", "numPlays", "albums"), fieldNames(detail));
        assertEquals(ALBUM_ID, detail.get("albums").get(0).asText());
        assertEquals(1, EXTERNAL_CALLS.get(), "all request variants should share one cached catalog");
    }

    @Test
    void shouldReturnStandardized503ForUpstreamHttpFailure() throws Exception {
        UPSTREAM_RESPONSE.set(new UpstreamResponse(503, "upstream unavailable", 0));

        HttpResponse<String> response = get("/api/bands");

        assertErrorResponse(response, 503, "Service Unavailable", "/api/bands");
        assertEquals(1, EXTERNAL_CALLS.get());
    }

    @Test
    void shouldReturnStandardized502ForInvalidUpstreamJson() throws Exception {
        UPSTREAM_RESPONSE.set(new UpstreamResponse(200, "{invalid-json", 0));

        HttpResponse<String> response = get("/api/bands");

        assertErrorResponse(response, 502, "Bad Gateway", "/api/bands");
    }

    @Test
    void shouldReturnStandardized504ForUpstreamReadTimeout() throws Exception {
        UPSTREAM_RESPONSE.set(new UpstreamResponse(200, CATALOG_JSON, 1_000));

        HttpResponse<String> response = get("/api/bands");

        assertErrorResponse(response, 504, "Gateway Timeout", "/api/bands");
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + applicationPort + path))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void assertErrorResponse(HttpResponse<String> response, int status, String error, String path)
            throws IOException {
        assertEquals(status, response.statusCode());
        JsonNode body = objectMapper.readTree(response.body());
        assertTrue(body.hasNonNull("timestamp"));
        assertEquals(status, body.get("status").asInt());
        assertEquals(error, body.get("error").asText());
        assertTrue(body.hasNonNull("message"));
        assertEquals(path, body.get("path").asText());
        assertFalse(body.has("stackTrace"));
        assertEquals(Set.of("timestamp", "status", "error", "message", "path"), fieldNames(body));
    }

    private Set<String> fieldNames(JsonNode node) {
        var names = new java.util.HashSet<String>();
        node.fieldNames().forEachRemaining(names::add);
        return Set.copyOf(names);
    }

    private static synchronized String externalApiUrl() {
        if (externalServer == null) {
            try {
                externalServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                externalExecutor = Executors.newCachedThreadPool(task -> {
                    Thread thread = new Thread(task, "bands-external-stub");
                    thread.setDaemon(true);
                    return thread;
                });
                externalServer.setExecutor(externalExecutor);
                externalServer.createContext("/api/bands", BandsEndToEndTest::handleExternalRequest);
                externalServer.start();
            } catch (IOException exception) {
                throw new IllegalStateException("Could not start external API test server", exception);
            }
        }
        return "http://127.0.0.1:" + externalServer.getAddress().getPort() + "/api/bands";
    }

    private static void handleExternalRequest(HttpExchange exchange) throws IOException {
        EXTERNAL_CALLS.incrementAndGet();
        UpstreamResponse response = UPSTREAM_RESPONSE.get();
        try {
            if (response.delayMillis() > 0) {
                Thread.sleep(response.delayMillis());
            }
            byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(response.status(), body.length);
            exchange.getResponseBody().write(body);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException ignored) {
            // The client may close the socket after its configured read timeout.
        } finally {
            exchange.close();
        }
    }

    private record UpstreamResponse(int status, String body, long delayMillis) {}
}

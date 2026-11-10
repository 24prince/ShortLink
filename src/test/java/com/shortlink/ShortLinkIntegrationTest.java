package com.shortlink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortlink.service.ClickCounter;

/**
 * End-to-end test against real MySQL and Redis (via Testcontainers).
 * Skipped automatically when Docker is not available.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "shortlink.trust-proxy=true",
                "shortlink.rate-limit.create.capacity=5",
                "shortlink.rate-limit.create.refill-per-second=0.001"
        })
@Testcontainers(disabledWithoutDocker = true)
class ShortLinkIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    ClickCounter clickCounter;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private HttpResponse<String> send(String method, String path, String jsonBody, String clientIp)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("X-Forwarded-For", clientIp);
        if (jsonBody != null) {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(jsonBody));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return client.send(builder.build(), BodyHandlers.ofString());
    }

    private static String uniqueIp() {
        return "10." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250) + "."
                + (int) (Math.random() * 250);
    }

    @Test
    void createRedirectAndCountClicks() throws Exception {
        String ip = uniqueIp();
        HttpResponse<String> created = send("POST", "/api/v1/urls",
                "{\"url\":\"https://example.com/some/long/path\"}", ip);
        assertEquals(201, created.statusCode());

        JsonNode json = mapper.readTree(created.body());
        String code = json.get("shortCode").asText();
        assertNotNull(code);
        assertTrue(code.length() >= 6);

        // First hit goes to MySQL and warms the cache, second is served from Redis.
        HttpResponse<String> first = send("GET", "/" + code, null, ip);
        HttpResponse<String> second = send("GET", "/" + code, null, ip);
        assertEquals(302, first.statusCode());
        assertEquals("https://example.com/some/long/path", first.headers().firstValue("Location").orElse(""));
        assertEquals(302, second.statusCode());

        clickCounter.flush();

        HttpResponse<String> stats = send("GET", "/api/v1/urls/" + code + "/stats", null, ip);
        assertEquals(200, stats.statusCode());
        assertEquals(2, mapper.readTree(stats.body()).get("totalClicks").asLong());
    }

    @Test
    void duplicateCustomAliasReturnsConflict() throws Exception {
        String ip = uniqueIp();
        String alias = "al" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String body = "{\"url\":\"https://example.com\",\"customAlias\":\"" + alias + "\"}";

        assertEquals(201, send("POST", "/api/v1/urls", body, ip).statusCode());
        assertEquals(409, send("POST", "/api/v1/urls", body, ip).statusCode());
    }

    @Test
    void invalidUrlReturnsBadRequest() throws Exception {
        HttpResponse<String> response = send("POST", "/api/v1/urls",
                "{\"url\":\"javascript:alert(1)\"}", uniqueIp());
        assertEquals(400, response.statusCode());
    }

    @Test
    void unknownCodeReturnsNotFound() throws Exception {
        assertEquals(404, send("GET", "/doesnotexist", null, uniqueIp()).statusCode());
    }

    @Test
    void createEndpointIsRateLimitedPerClient() throws Exception {
        String ip = uniqueIp();
        for (int i = 0; i < 5; i++) {
            HttpResponse<String> ok = send("POST", "/api/v1/urls",
                    "{\"url\":\"https://example.com/" + i + "\"}", ip);
            assertEquals(201, ok.statusCode());
        }

        HttpResponse<String> blocked = send("POST", "/api/v1/urls",
                "{\"url\":\"https://example.com/blocked\"}", ip);
        assertEquals(429, blocked.statusCode());
        assertTrue(blocked.headers().firstValue("Retry-After").isPresent());

        // A different client is unaffected.
        HttpResponse<String> otherClient = send("POST", "/api/v1/urls",
                "{\"url\":\"https://example.com/other\"}", uniqueIp());
        assertEquals(201, otherClient.statusCode());
    }
}

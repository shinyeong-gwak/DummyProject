package com.example.dummy;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class ApiIntegrationTest {
    public static void main(String[] args) throws Exception {
        var server = Application.createServer(0, "test-secret");
        server.start();
        String base = "http://localhost:" + server.getAddress().getPort();
        try (var client = HttpClient.newHttpClient()) {
            check(client, base, "/health", "GET", "", null, 200, "\"UP\"");
            check(client, base, "/api/hello", "GET", "", null, 200, "Hello from Java!");
            check(client, base, "/missing", "GET", "", null, 404, "Not found");
            check(client, base, "/health/extra", "GET", "", null, 404, "Not found");
            check(client, base, "/health", "POST", "", null, 405, "Method not allowed");
            check(client, base, "/hooks/github", "POST", "{}", null, 401, "Invalid signature");
            check(client, base, "/hooks/github", "POST", "{}", "sha256=invalid", 401, "Invalid signature");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = "sha256=" + HexFormat.of().formatHex(mac.doFinal("{}".getBytes(StandardCharsets.UTF_8)));
            check(client, base, "/hooks/github", "POST", "{}", signature, 202, "\"accepted\":true");
            check(client, base, "/hooks/github", "POST", "{ }", signature, 401, "Invalid signature");
            check(client, base, "/hooks/github", "POST", "x".repeat(65_537), signature, 413, "Payload too large");
        } finally { server.stop(0); }
        var disabled = Application.createServer(0, null);
        disabled.start();
        try (var client = HttpClient.newHttpClient()) {
            check(client, "http://localhost:" + disabled.getAddress().getPort(), "/hooks/github", "POST", "{}", null, 503, "not configured");
        } finally { disabled.stop(0); }
        System.out.println("All 11 HTTP integration checks passed.");
    }

    private static void check(HttpClient client, String base, String path, String method, String body,
                              String signature, int expected, String text) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(java.time.Duration.ofSeconds(5))
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        if (signature != null) request.header("X-Hub-Signature-256", signature);
        var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != expected || !response.body().contains(text))
            throw new AssertionError(path + ": " + response.statusCode() + " " + response.body());
    }
}

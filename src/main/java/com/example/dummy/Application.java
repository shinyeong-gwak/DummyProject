package com.example.dummy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.Executors;

public final class Application {
    private static final int MAX_BODY = 65_536;

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = createServer(port, System.getenv("WEBHOOK_SECRET"));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(1)));
        server.start();
        System.out.println("Dummy API listening on port " + server.getAddress().getPort());
    }

    public static HttpServer createServer(int port, String secret) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", exchange -> {
            try {
                String path = exchange.getRequestURI().getPath();
                String method = exchange.getRequestMethod();
                switch (path) {
                    case "/health" -> {
                        if (!requireMethod(exchange, method, "GET")) return;
                        reply(exchange, 200, "{\"status\":\"UP\"}");
                    }
                    case "/api/hello" -> {
                        if (!requireMethod(exchange, method, "GET")) return;
                        reply(exchange, 200, "{\"message\":\"Hello from Java!\"}");
                    }
                    case "/hooks/github" -> {
                        if (!requireMethod(exchange, method, "POST")) return;
                        if (secret == null || secret.isBlank()) {
                            reply(exchange, 503, "{\"error\":\"WEBHOOK_SECRET is not configured\"}");
                            return;
                        }
                        byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
                        if (body.length > MAX_BODY) {
                            reply(exchange, 413, "{\"error\":\"Payload too large\"}");
                            return;
                        }
                        String signature = exchange.getRequestHeaders().getFirst("X-Hub-Signature-256");
                        if (!validSignature(body, secret, signature)) {
                            reply(exchange, 401, "{\"error\":\"Invalid signature\"}");
                            return;
                        }
                        // 실험용 수신 확인만 수행합니다. 전달받은 명령이나 코드는 실행하지 않습니다.
                        reply(exchange, 202, "{\"accepted\":true,\"bytes\":" + body.length + "}");
                    }
                    default -> reply(exchange, 404, "{\"error\":\"Not found\"}");
                }
            } finally {
                exchange.close();
            }
        });
        return server;
    }

    private static boolean requireMethod(HttpExchange exchange, String actual, String expected) throws IOException {
        if (actual.equals(expected)) return true;
        exchange.getResponseHeaders().set("Allow", expected);
        reply(exchange, 405, "{\"error\":\"Method not allowed\"}");
        return false;
    }

    private static boolean validSignature(byte[] body, String secret, String signature) {
        if (signature == null || !signature.matches("sha256=[0-9a-fA-F]{64}")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return MessageDigest.isEqual(mac.doFinal(body), HexFormat.of().parseHex(signature.substring(7)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void reply(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}

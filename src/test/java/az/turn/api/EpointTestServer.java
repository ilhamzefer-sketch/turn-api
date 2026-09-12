package az.turn.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

final class EpointTestServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ObjectMapper mapper = new ObjectMapper();
    final AtomicInteger calls = new AtomicInteger();
    volatile Consumer<JsonNode> beforeResponse = payload -> {};
    volatile JsonNode lastPayload;
    volatile int responseCode = 200;

    EpointTestServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/payment-request", this::checkout);
        server.setExecutor(executor);
        server.start();
    }

    String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

    void reset() {
        calls.set(0);
        beforeResponse = payload -> {};
        lastPayload = null;
        responseCode = 200;
    }

    private void checkout(HttpExchange exchange) throws IOException {
        calls.incrementAndGet();
        String form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String encoded = URLDecoder.decode(form.substring(5, form.indexOf('&')), StandardCharsets.UTF_8);
        JsonNode payload = mapper.readTree(Base64.getDecoder().decode(encoded));
        lastPayload = payload;
        beforeResponse.accept(payload);
        byte[] response = mapper.writeValueAsBytes(Map.of("status", "success", "redirect_url",
                "https://epoint.test/checkout/" + payload.path("order_id").asText(),
                "transaction", "tx-" + payload.path("order_id").asText()));
        try (exchange) {
            exchange.sendResponseHeaders(responseCode, response.length);
            exchange.getResponseBody().write(response);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}

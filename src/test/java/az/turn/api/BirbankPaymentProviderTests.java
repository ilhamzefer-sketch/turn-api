package az.turn.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BirbankPaymentProviderTests {
    private HttpServer server;
    private volatile String responseBody;
    private BirbankPaymentProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/order/123", exchange -> {
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        provider = new BirbankPaymentProvider(new ObjectMapper(),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/api",
                "http://127.0.0.1:5173", "test-user", "test-password");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void confirmsOnlyMatchingFullyPaidOrder() {
        responseBody = order("FullyPaid", 20, "AZN", 20);
        assertEquals(PaymentStatus.COMPLETED, provider.confirm(session()));
    }

    @Test
    void keepsPreparingOrderPending() {
        responseBody = order("Preparing", 20, "AZN", 0);
        assertEquals(PaymentStatus.PENDING, provider.confirm(session()));
    }

    @Test
    void mapsDeclinedOrderToCancelled() {
        responseBody = order("Declined", 20, "AZN", 0);
        assertEquals(PaymentStatus.CANCELLED, provider.confirm(session()));
    }

    @Test
    void mapsExpiredOrderToCancelled() {
        responseBody = order("Expired", 20, "AZN", 0);
        assertEquals(PaymentStatus.CANCELLED, provider.confirm(session()));
    }

    @Test
    void rejectsPaidOrderWithWrongAmount() {
        responseBody = order("FullyPaid", 10, "AZN", 10);
        assertThrows(ResponseStatusException.class, () -> provider.confirm(session()));
    }

    private PaymentSessionEntity session() {
        PaymentSessionEntity session = new PaymentSessionEntity();
        session.setId(99L);
        session.setExternalOrderId("123");
        session.setAmount(20);
        session.setCurrency("AZN");
        return session;
    }

    private String order(String status, int amount, String currency, int clearedAmount) {
        return "{\"order\":{\"id\":123,\"status\":\"" + status + "\",\"amount\":" + amount
                + ",\"currency\":\"" + currency + "\",\"authorizedChargeAmount\":" + clearedAmount
                + ",\"clearedChargeAmount\":" + clearedAmount + "}}";
    }
}

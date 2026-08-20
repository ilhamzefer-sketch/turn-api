package az.turn.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbbPaymentProviderTests {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private AbbPaymentProvider provider;
    private String fileStatus = "IN_PROGRESS";
    private String submittedXml;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/payments/auth/token", this::authenticate);
        server.createContext("/payments/file-status", this::fileStatus);
        server.createContext("/payments/", this::acceptPayment);
        server.start();
        provider = new AbbPaymentProvider(
                objectMapper,
                new AbbPaymentXmlFactory(),
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "test-user",
                "test-password",
                "AZ00TEST00000000000000000001",
                "E-Novbe Test",
                "AZ00TEST00000000000000000002",
                "3689677",
                "805250"
        );
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void createsAbbPaymentFileAndKeepsProcessingPaymentPending() {
        PaymentSessionEntity session = session();

        provider.initialize(session);

        assertEquals("batch-42", session.getExternalOrderId());
        assertEquals("ENOVBE-42", session.getPaymentReference());
        assertTrue(submittedXml.contains("<type>IN</type>"));
        assertTrue(submittedXml.contains("<amount>20</amount>"));
        assertTrue(submittedXml.contains("<recipientBankCode>805250</recipientBankCode>"));
        assertEquals(PaymentStatus.PENDING, provider.confirm(session));
    }

    @Test
    void completesPaymentOnlyWhenAbbCompletesTheFile() {
        PaymentSessionEntity session = session();
        provider.initialize(session);
        fileStatus = "COMPLETED";

        assertEquals(PaymentStatus.COMPLETED, provider.confirm(session));
    }

    @Test
    void mapsFailedPaymentFileToFailed() {
        PaymentSessionEntity session = session();
        provider.initialize(session);
        fileStatus = "FAILURE";

        assertEquals(PaymentStatus.FAILED, provider.confirm(session));
    }

    @Test
    void rejectsStatusThatDoesNotMatchTheSavedBatch() {
        PaymentSessionEntity session = session();
        provider.initialize(session);
        session.setExternalOrderId("another-batch");

        assertThrows(ResponseStatusException.class, () -> provider.confirm(session));
    }

    private PaymentSessionEntity session() {
        PaymentSessionEntity session = new PaymentSessionEntity();
        session.setId(42L);
        session.setAmount(20);
        session.setCurrency("AZN");
        return session;
    }

    private void authenticate(HttpExchange exchange) throws IOException {
        write(exchange, 200, "{\"access_token\":\"test-token\",\"expires_in\":3600}");
    }

    private void acceptPayment(HttpExchange exchange) throws IOException {
        JsonNode request = objectMapper.readTree(exchange.getRequestBody());
        submittedXml = new String(
                Base64.getDecoder().decode(request.path("base64aDoc").asText()),
                StandardCharsets.UTF_8
        );
        assertEquals("ENOVBE-42", request.path("externalReference").asText());
        write(exchange, 200, "{\"data\":{\"batchNumber\":\"batch-42\"}}");
    }

    private void fileStatus(HttpExchange exchange) throws IOException {
        String body = "{\"externalReference\":\"ENOVBE-42\",\"batchNumber\":\"batch-42\","
                + "\"status\":{\"status\":\"" + fileStatus + "\",\"description\":\"" + fileStatus + "\"}}";
        write(exchange, 200, body);
    }

    private void write(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}

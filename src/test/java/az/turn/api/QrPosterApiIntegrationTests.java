package az.turn.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import jakarta.servlet.http.Cookie;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.rate-limit.auth-per-minute=100")
@AutoConfigureMockMvc
class QrPosterApiIntegrationTests {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void persistsEditableTitleRegeneratesBrandedPdfAndProtectsOwnership() throws Exception {
        TestCsrfToken csrf = csrf();
        String ownerToken = register(csrf, "0507999101", "Poster");
        long roomId = createRoom(csrf, ownerToken);
        JsonNode credential = createQr(csrf, ownerToken, roomId);

        MvcResult updateResult = mockMvc.perform(patch(
                                "/api/rooms/{roomId}/qr-codes/{credentialId}", roomId, credential.get("id").asLong())
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"posterTitle\":\"  Qapı   ustası  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posterTitle").value("Qapı ustası"))
                .andReturn();
        JsonNode updated = objectMapper.readTree(updateResult.getResponse().getContentAsString());

        MvcResult regenerateResult = mockMvc.perform(post(
                                "/api/rooms/{roomId}/qr-codes/{credentialId}/regenerate", roomId, updated.get("id").asLong())
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.posterTitle").value("Qapı ustası"))
                .andReturn();
        JsonNode regenerated = objectMapper.readTree(regenerateResult.getResponse().getContentAsString());

        MvcResult pdfResult = mockMvc.perform(get(
                                "/api/rooms/{roomId}/qr-codes/{credentialId}/poster.pdf",
                                roomId,
                                regenerated.get("id").asLong())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString(".pdf")))
                .andReturn();

        String expectedUrl = "https://novbetime.az/q/" + regenerated.get("token").asText();
        assertPdf(pdfResult.getResponse().getContentAsByteArray(), expectedUrl);

        String strangerToken = register(csrf, "0507999102", "Stranger");
        mockMvc.perform(get(
                                "/api/rooms/{roomId}/qr-codes/{credentialId}/poster.pdf",
                                roomId,
                                regenerated.get("id").asLong())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsPosterTitlesLongerThanEightyCharacters() throws Exception {
        TestCsrfToken csrf = csrf();
        String ownerToken = register(csrf, "0507999103", "Boundary");
        long roomId = createRoom(csrf, ownerToken);
        JsonNode credential = createQr(csrf, ownerToken, roomId);
        String title = "x".repeat(81);

        mockMvc.perform(patch("/api/rooms/{roomId}/qr-codes/{credentialId}", roomId, credential.get("id").asLong())
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("posterTitle", title))))
                .andExpect(status().isBadRequest());
    }

    private void assertPdf(byte[] bytes, String expectedUrl) throws Exception {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(document.getPage(0).getMediaBox().getWidth()).isCloseTo(595.28f, offset(0.1f));
            assertThat(document.getPage(0).getMediaBox().getHeight()).isCloseTo(841.89f, offset(0.1f));
            assertThat(document.getDocumentInformation().getAuthor()).isEqualTo("NövbəTime");
            String visibleText = new PDFTextStripper().getText(document);
            assertThat(visibleText).contains("Qapı ustası", "NövbəTime", "novbetime.az");
            assertThat(visibleText).doesNotContain(expectedUrl, "file://", "1/1");
            BufferedImage image = new PDFRenderer(document).renderImageWithDPI(0, 240);
            BufferedImageLuminanceSource source = new BufferedImageLuminanceSource(image);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.TRY_HARDER, true);
            Result decoded = new MultiFormatReader().decode(bitmap, hints);
            assertThat(decoded.getText()).isEqualTo(expectedUrl);
        }
    }

    private JsonNode createQr(TestCsrfToken csrf, String accessToken, long roomId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/rooms/{roomId}/qr-codes", roomId)
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.posterTitle").isEmpty())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private long createRoom(TestCsrfToken csrf, String accessToken) throws Exception {
        MvcResult workspaceResult = mockMvc.perform(post("/api/individual-workspaces")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Poster workspace\",\"timezone\":\"Asia/Baku\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long workspaceId = objectMapper.readTree(workspaceResult.getResponse().getContentAsString()).get("id").asLong();
        MvcResult roomResult = mockMvc.perform(post("/api/individual-workspaces/{workspaceId}/rooms", workspaceId)
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Əsas qəbul\",\"roomNumberOrCode\":\"A147\"," 
                                + "\"description\":\"Müştəri qəbulu\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(roomResult.getResponse().getContentAsString()).get("id").asLong();
    }

    private String register(TestCsrfToken csrf, String phone, String firstName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"firstName\":\"" + firstName + "\"," 
                                + "\"lastName\":\"Owner\",\"password\":\"Poster-safe-2026\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private TestCsrfToken csrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        Cookie cookie = result.getResponse().getCookie(CsrfCookieFilter.CSRF_COOKIE_NAME);
        String value = objectMapper.readTree(result.getResponse().getContentAsString()).get("csrfToken").asText();
        assertThat(cookie).isNotNull();
        return new TestCsrfToken(cookie, value);
    }

}

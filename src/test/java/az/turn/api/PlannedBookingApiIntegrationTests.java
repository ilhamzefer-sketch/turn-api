package az.turn.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.rate-limit.auth-per-minute=100")
@AutoConfigureMockMvc
class PlannedBookingApiIntegrationTests {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void runsPublicAvailabilityCustomerBookingAndOperatorCancellationFlow() throws Exception {
        TestCsrfToken csrf = csrf();
        String ownerToken = register(csrf, "0507500001");
        String customerToken = register(csrf, "0507500002");
        String strangerToken = register(csrf, "0507500003");
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Baku")).plusDays(1);
        long roomId = createPublishedRoom(csrf, ownerToken, date);

        MvcResult slotsResult = mockMvc.perform(get("/api/public/rooms/{roomId}/available-slots", roomId)
                        .param("date", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].startAt").isNotEmpty())
                .andReturn();
        String startAt = objectMapper.readTree(slotsResult.getResponse().getContentAsString())
                .get(0).get("startAt").asText();

        MvcResult bookingResult = mockMvc.perform(post("/api/bookings")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":" + roomId + ",\"startAt\":\"" + startAt + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.bookingReference").isNotEmpty())
                .andReturn();
        long bookingId = objectMapper.readTree(bookingResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/customers/me/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(bookingId));
        mockMvc.perform(get("/api/bookings/{bookingId}", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/rooms/{roomId}/bookings", roomId)
                        .param("date", date.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].participantPhone").value("+994507500002"));
        mockMvc.perform(post("/api/rooms/{roomId}/bookings/{bookingId}/cancel", roomId, bookingId)
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Otaq bağlıdır\",\"participantInformed\":false}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/rooms/{roomId}/bookings/{bookingId}/cancel", roomId, bookingId)
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Otaq bağlıdır\",\"participantInformed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("OWNER_CANCELLED"));
    }

    @Test
    void rejectsUnauthenticatedBookingCreation() throws Exception {
        TestCsrfToken csrf = csrf();
        mockMvc.perform(post("/api/bookings")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":1,\"startAt\":\"2026-09-01T10:00:00\"}"))
                .andExpect(status().isUnauthorized());
    }

    private long createPublishedRoom(TestCsrfToken csrf, String token, LocalDate date) throws Exception {
        MvcResult workspaceResult = mockMvc.perform(post("/api/individual-workspaces")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Booking workspace\",\"timezone\":\"Asia/Baku\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long workspaceId = objectMapper.readTree(workspaceResult.getResponse().getContentAsString()).get("id").asLong();
        MvcResult roomResult = mockMvc.perform(post("/api/individual-workspaces/{workspaceId}/rooms", workspaceId)
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Planlı qəbul\",\"reservationMode\":\"PLANNED_BOOKING\","
                                + "\"defaultSlotDurationMinutes\":30,\"visibility\":\"UNLISTED\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long roomId = objectMapper.readTree(roomResult.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(put("/api/rooms/{roomId}/availability-rules", roomId)
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rules\":[{\"dayOfWeek\":\"" + date.getDayOfWeek() + "\","
                                + "\"startTime\":\"09:00\",\"endTime\":\"12:00\",\"active\":true}]}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/rooms/{roomId}/configuration", roomId)
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultSlotDurationMinutes\":30,\"appointmentBufferMinutes\":0,"
                                + "\"bookingWindowDays\":30,\"minimumAdvanceMinutes\":0,"
                                + "\"cancellationCutoffMinutes\":0,\"liveQueueAcceptingNewEntries\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/rooms/{roomId}/publish", roomId)
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        return roomId;
    }

    private String register(TestCsrfToken csrf, String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"firstName\":\"Booking\","
                                + "\"lastName\":\"User\",\"password\":\"Booking-safe-2026\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private TestCsrfToken csrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        Cookie cookie = result.getResponse().getCookie(CsrfCookieFilter.CSRF_COOKIE_NAME);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new TestCsrfToken(cookie, body.get("csrfToken").asText());
    }
}

package az.turn.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.legacy-api.enabled=false")
@AutoConfigureMockMvc
class StepSixApiIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void disablesLegacyQueueApiByDefault() throws Exception {
        mockMvc.perform(get("/api/queues/public"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value(
                        "Bu köhnə API bağlanıb. Telefon əsaslı yeni API-dən istifadə edin."
                ));
    }

    @Test
    void exposesSubscriptionPlansWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/subscriptions/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("STANDARD_MONTHLY"));
    }
}

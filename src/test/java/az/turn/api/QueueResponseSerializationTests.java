package az.turn.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class QueueResponseSerializationTests {

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private QueueService queueService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void serializesCategoriesAfterServiceTransactionCloses() throws Exception {
        RegistrationEntity registration = new RegistrationEntity();
        registration.setFirstName("Test");
        registration.setLastName("Creator");
        registration.setEmail("queue-response-" + UUID.randomUUID() + "@example.com");
        registration.setPasswordHash("test-hash");
        registration.setPaid(true);
        registration.setPaymentReference("TEST-REFERENCE");
        registration.setRegistrationType(RegistrationType.FERDI);
        registration.setStatus(RegistrationStatus.ACTIVE);
        registration = registrationRepository.save(registration);

        QueueEntity queue = new QueueEntity();
        queue.setRegistration(registration);
        queue.setAddress("Test address");
        queue.setServiceName("Test service");
        queue.setCategories(List.of("Test category"));
        queue.setQrToken(UUID.randomUUID().toString());
        queue.setCurrentServingNumber(0);
        queue.setLastIssuedNumber(0);
        queue.setAverageServiceMinutes(5);
        queue.setServedCustomersCount(0);
        queue.setTotalServiceMinutes(0);
        queue.setResetMode(QueueResetMode.DAILY);
        queue.setResetAt(LocalDateTime.now().plusDays(1));
        queue.setActive(true);
        queue = queueRepository.save(queue);

        try {
            QueueResponse response = queueService.getQueues(registration.getId()).get(0);

            assertThat(objectMapper.writeValueAsString(response))
                    .contains("\"categories\":[\"Test category\"]");
        } finally {
            queueRepository.deleteById(queue.getId());
            registrationRepository.deleteById(registration.getId());
        }
    }
}

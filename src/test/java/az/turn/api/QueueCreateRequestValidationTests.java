package az.turn.api;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QueueCreateRequestValidationTests {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void allowsEmptyManagerCredentialsForIndividualQueueRequest() {
        QueueCreateRequest request = new QueueCreateRequest(
                1L,
                "Bakı şəhəri",
                "Kardioloq qəbulu",
                List.of("Kardiologiya"),
                "DAILY",
                "",
                "",
                ""
        );

        assertTrue(validator.validate(request).isEmpty());
    }
}

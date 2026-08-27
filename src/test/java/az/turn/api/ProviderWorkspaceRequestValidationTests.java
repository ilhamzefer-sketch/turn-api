package az.turn.api;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderWorkspaceRequestValidationTests {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void individualWorkspaceNameMustContainAtLeastTwoCharacters() {
        IndividualWorkspaceCreateRequestDto request = new IndividualWorkspaceCreateRequestDto("A", "Asia/Baku");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("name");
    }

    @Test
    void roomNameMustContainAtLeastTwoCharacters() {
        RoomUpsertRequestDto request = new RoomUpsertRequestDto(
                "A",
                null,
                null,
                null,
                "Asia/Baku",
                ReservationMode.LIVE_QUEUE,
                30,
                RoomVisibility.UNLISTED,
                null,
                null,
                null
        );

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("name");
    }
}

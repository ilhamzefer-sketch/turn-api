package az.turn.api;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhoneNumberServiceTests {
    private final PhoneNumberService phoneNumberService = new PhoneNumberService();

    @ParameterizedTest
    @ValueSource(strings = {"0501234567", "501234567", "+994501234567", "994501234567", "(050) 123-45-67"})
    void normalizesSupportedAzerbaijaniPhoneFormats(String input) {
        assertEquals("+994501234567", phoneNumberService.normalizeAzerbaijaniPhone(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "+995501234567", "05012345", "phone"})
    void rejectsUnsupportedPhoneFormats(String input) {
        assertThrows(ResponseStatusException.class, () -> phoneNumberService.normalizeAzerbaijaniPhone(input));
    }
}

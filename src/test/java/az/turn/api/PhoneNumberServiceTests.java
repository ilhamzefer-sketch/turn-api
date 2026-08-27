package az.turn.api;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhoneNumberServiceTests {
    private final PhoneNumberService phoneNumberService = new PhoneNumberService();

    @ParameterizedTest
    @ValueSource(strings = {"0501234567", "0707654321", "0991234567"})
    void normalizesExactLocalAzerbaijaniPhoneFormat(String input) {
        assertEquals("+994" + input.substring(1), phoneNumberService.normalizeAzerbaijaniPhone(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "501234567",
            "+994501234567",
            "994501234567",
            "(050) 123-45-67",
            "050 123 45 67",
            "050-123-45-67",
            "050123456",
            "05012345678",
            "0001234567",
            "phone"
    })
    void rejectsEveryFormatExceptExactTenDigitLocalNumber(String input) {
        assertThrows(ResponseStatusException.class, () -> phoneNumberService.normalizeAzerbaijaniPhone(input));
    }
}

package az.turn.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionSecurityValidatorTests {

    @Test
    void rejectsUnsafeProductionConfiguration() {
        ProductionSecurityValidator validator = new ProductionSecurityValidator(
                "prod", "replace-with-a-random-secret-of-at-least-32-characters",
                List.of("http://app.example.com"), "admin", "not-bcrypt",
                true, true, "memory", false, false
        );
        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    void acceptsCompleteProductionConfiguration() {
        ProductionSecurityValidator validator = new ProductionSecurityValidator(
                "prod", "Kq9vL8nR2sT7xW4zY6bC1dF3gH5jM0pQ8uV2aN7e",
                List.of("https://app.example.com"), "turn-admin",
                "$2a$12$123456789012345678901u12345678901234567890123456789012",
                false, false, "redis", true, true
        );
        assertDoesNotThrow(validator::validate);
    }

    @Test
    void acceptsSecureProductionConfigurationWithBankPaymentsRetired() {
        ProductionSecurityValidator validator = new ProductionSecurityValidator(
                "prod", "Kq9vL8nR2sT7xW4zY6bC1dF3gH5jM0pQ8uV2aN7e",
                List.of("https://app.example.com"), "turn-admin",
                "$2a$12$123456789012345678901u12345678901234567890123456789012",
                false, false, "redis", true, true
        );
        assertDoesNotThrow(validator::validate);
    }

    @Test
    void rejectsLegacyBankApiInProduction() {
        ProductionSecurityValidator validator = new ProductionSecurityValidator(
                "prod", "Kq9vL8nR2sT7xW4zY6bC1dF3gH5jM0pQ8uV2aN7e",
                List.of("https://app.example.com"), "turn-admin",
                "$2a$12$123456789012345678901u12345678901234567890123456789012",
                true, false, "redis", true, true
        );
        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    void rejectsPaymentReconciliationInProduction() {
        ProductionSecurityValidator validator = new ProductionSecurityValidator(
                "prod", "Kq9vL8nR2sT7xW4zY6bC1dF3gH5jM0pQ8uV2aN7e",
                List.of("https://app.example.com"), "turn-admin",
                "$2a$12$123456789012345678901u12345678901234567890123456789012",
                false, true, "redis", true, true
        );
        assertThrows(IllegalStateException.class, validator::validate);
    }
}

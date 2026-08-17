package az.turn.api;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserPasswordServiceTests {
    private final UserPasswordService userPasswordService = new UserPasswordService(new BCryptPasswordEncoder());

    @Test
    void supportsPasswordsLongerThanBcryptInputLimit() {
        String password = "A".repeat(127) + "1";
        String encoded = userPasswordService.encode(password);

        assertThat(userPasswordService.matches(password, encoded)).isTrue();
        assertThat(userPasswordService.matches(password + "x", encoded)).isFalse();
    }

    @Test
    void rejectsCommonlyCompromisedPassword() {
        assertThrows(ResponseStatusException.class, () -> userPasswordService.encode("password1"));
    }
}

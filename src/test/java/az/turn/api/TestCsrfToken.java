package az.turn.api;

import jakarta.servlet.http.Cookie;

public record TestCsrfToken(
        Cookie cookie,
        String value
) {
}

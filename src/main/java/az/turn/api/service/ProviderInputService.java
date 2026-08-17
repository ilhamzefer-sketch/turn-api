package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.ZoneId;

@Service
public class ProviderInputService {
    private static final String DEFAULT_TIMEZONE = "Asia/Baku";

    public String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    public String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public String timezone(String value, String fallback) {
        String candidate = optional(value);
        if (candidate == null) candidate = optional(fallback);
        if (candidate == null) candidate = DEFAULT_TIMEZONE;
        try {
            return ZoneId.of(candidate).getId();
        } catch (DateTimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saat qurşağı düzgün deyil.");
        }
    }
}

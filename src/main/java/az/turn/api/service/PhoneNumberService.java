package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PhoneNumberService {

    public String normalizeAzerbaijaniPhone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            throw invalidPhone();
        }

        String compact = rawPhone.trim().replaceAll("[\\s()\\-]", "");
        if (compact.startsWith("00")) {
            compact = "+" + compact.substring(2);
        }

        String digits = compact.startsWith("+") ? compact.substring(1) : compact;
        if (!digits.matches("\\d+")) {
            throw invalidPhone();
        }

        String nationalNumber;
        if (digits.startsWith("994")) {
            nationalNumber = digits.substring(3);
        } else if (digits.startsWith("0")) {
            nationalNumber = digits.substring(1);
        } else {
            nationalNumber = digits;
        }

        if (!nationalNumber.matches("[1-9]\\d{8}")) {
            throw invalidPhone();
        }
        return "+994" + nationalNumber;
    }

    private ResponseStatusException invalidPhone() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Telefon nömrəsi düzgün Azərbaycan formatında deyil.");
    }
}

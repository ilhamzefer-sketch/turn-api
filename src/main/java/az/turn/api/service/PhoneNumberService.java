package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PhoneNumberService {

    public String normalizeAzerbaijaniPhone(String rawPhone) {
        if (rawPhone == null || !rawPhone.matches("0[1-9]\\d{8}")) {
            throw invalidPhone();
        }
        return "+994" + rawPhone.substring(1);
    }

    private ResponseStatusException invalidPhone() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Telefon nömrəsini 0504059961 formatında yazın.");
    }
}

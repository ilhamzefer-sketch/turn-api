package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

@Service
public class UserPasswordService {
    private static final String HASH_PREFIX = "{bcrypt-sha256}";
    private static final Set<String> COMPROMISED_PASSWORDS = Set.of(
            "12345678", "123456789", "password", "password1", "qwerty123", "admin123", "iloveyou"
    );

    private final PasswordEncoder passwordEncoder;

    public UserPasswordService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public String encode(String password) {
        validate(password);
        return HASH_PREFIX + passwordEncoder.encode(preHash(password));
    }

    public boolean matches(String password, String storedHash) {
        if (password == null || storedHash == null || !storedHash.startsWith(HASH_PREFIX)) {
            return false;
        }
        return passwordEncoder.matches(preHash(password), storedHash.substring(HASH_PREFIX.length()));
    }

    public void validate(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Şifrə 8-128 simvol olmalıdır.");
        }
        if (COMPROMISED_PASSWORDS.contains(password.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu şifrə çox geniş istifadə olunur. Daha təhlükəsiz şifrə seçin.");
        }
    }

    private String preHash(String password) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 mövcud deyil.", exception);
        }
    }
}

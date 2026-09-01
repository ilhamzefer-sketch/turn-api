package az.turn.api;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SecureStorageKeyGenerator {
    public String generate(String extension) {
        String identifier = UUID.randomUUID().toString();
        return identifier.substring(0, 2) + "/" + identifier + "." + extension;
    }
}

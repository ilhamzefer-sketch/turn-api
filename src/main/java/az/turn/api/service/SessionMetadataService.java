package az.turn.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class SessionMetadataService {

    public SessionMetadata from(HttpServletRequest request) {
        return new SessionMetadata(
                truncate(request.getHeader("User-Agent"), 500),
                truncate(request.getRemoteAddr(), 64)
        );
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}

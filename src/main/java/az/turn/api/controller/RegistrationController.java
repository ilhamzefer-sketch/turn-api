package az.turn.api;

import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
public class RegistrationController {

    private final QueueService queueService;
    private final RequestAuthenticationService requestAuthenticationService;

    public RegistrationController(QueueService queueService, RequestAuthenticationService requestAuthenticationService) {
        this.queueService = queueService;
        this.requestAuthenticationService = requestAuthenticationService;
    }

    @PostMapping("/api/registrations")
    public RegistrationResponse createRegistration(@RequestBody RegistrationRequest request) {
        throw new ResponseStatusException(HttpStatus.GONE, "BirbaÅŸa kart mÉ™lumatÄ± qÉ™bul edÉ™n kÃ¶hnÉ™ qeydiyyat baÄŸlanÄ±b. Bank Ã¶dÉ™niÅŸ sessiyasÄ±ndan istifadÉ™ edin.");
    }

    @GetMapping("/api/registrations/{registrationId}/queues")
    public List<QueueResponse> getQueues(@PathVariable @Positive long registrationId, Authentication authentication) {
        AuthenticatedUser user = requestAuthenticationService.requireUser(authentication, AuthUserType.REGISTRATION);
        if (!user.userId().equals(registrationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu novbelere baxmaq icazeniz yoxdur.");
        }
        return queueService.getQueues(registrationId);
    }
}

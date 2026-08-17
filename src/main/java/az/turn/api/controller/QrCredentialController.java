package az.turn.api;

import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/rooms/{roomId}/qr-codes")
public class QrCredentialController {
    private final QrCredentialService qrCredentialService;
    private final RequestAuthenticationService authenticationService;

    public QrCredentialController(
            QrCredentialService qrCredentialService,
            RequestAuthenticationService authenticationService
    ) {
        this.qrCredentialService = qrCredentialService;
        this.authenticationService = authenticationService;
    }

    @GetMapping
    public List<QrCredentialDto> list(
            @PathVariable @Positive long roomId,
            Authentication authentication
    ) {
        return qrCredentialService.list(roomId, userId(authentication));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QrCredentialDto create(
            @PathVariable @Positive long roomId,
            Authentication authentication
    ) {
        return qrCredentialService.create(roomId, userId(authentication));
    }

    @PostMapping("/{credentialId}/regenerate")
    @ResponseStatus(HttpStatus.CREATED)
    public QrCredentialDto regenerate(
            @PathVariable @Positive long roomId,
            @PathVariable @Positive long credentialId,
            Authentication authentication
    ) {
        return qrCredentialService.regenerate(roomId, credentialId, userId(authentication));
    }

    @DeleteMapping("/{credentialId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(
            @PathVariable @Positive long roomId,
            @PathVariable @Positive long credentialId,
            Authentication authentication
    ) {
        qrCredentialService.revoke(roomId, credentialId, userId(authentication));
    }

    private long userId(Authentication authentication) {
        return authenticationService.requireUser(authentication, AuthUserType.USER).userId();
    }
}

package az.turn.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

@RestController
@Validated
@RequestMapping("/api/rooms/{roomId}/qr-codes")
public class QrCredentialController {
    private final QrCredentialService qrCredentialService;
    private final QrPosterService qrPosterService;
    private final RequestAuthenticationService authenticationService;

    public QrCredentialController(
            QrCredentialService qrCredentialService,
            QrPosterService qrPosterService,
            RequestAuthenticationService authenticationService
    ) {
        this.qrCredentialService = qrCredentialService;
        this.qrPosterService = qrPosterService;
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

    @PatchMapping("/{credentialId}")
    public QrCredentialDto updatePosterTitle(
            @PathVariable @Positive long roomId,
            @PathVariable @Positive long credentialId,
            @Valid @RequestBody QrPosterTitleUpdateDto request,
            Authentication authentication
    ) {
        return qrCredentialService.updatePosterTitle(roomId, credentialId, userId(authentication), request);
    }

    @GetMapping(value = "/{credentialId}/poster.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<ByteArrayResource> downloadPoster(
            @PathVariable @Positive long roomId,
            @PathVariable @Positive long credentialId,
            Authentication authentication
    ) {
        QrPosterFile document = qrPosterService.create(roomId, credentialId, userId(authentication));
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(document.filename(), UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(document.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(new ByteArrayResource(document.bytes()));
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

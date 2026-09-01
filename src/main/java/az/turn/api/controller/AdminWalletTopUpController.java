package az.turn.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/admin/payments/top-ups")
public class AdminWalletTopUpController {
    private final RequestAuthenticationService authenticationService;
    private final AdminWalletTopUpService topUpService;

    public AdminWalletTopUpController(
            RequestAuthenticationService authenticationService,
            AdminWalletTopUpService topUpService
    ) {
        this.authenticationService = authenticationService;
        this.topUpService = topUpService;
    }

    @GetMapping
    public AdminTopUpRequestPageDto list(
            @RequestParam(required = false)
            @Pattern(regexp = "(?i)AWAITING_RECEIPT|PENDING_REVIEW|APPROVED|REJECTED|EXPIRED") String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        requireAdmin(authentication);
        return topUpService.list(status, page, size);
    }

    @GetMapping("/{requestId}")
    public AdminTopUpRequestDto get(
            @PathVariable @Positive long requestId,
            Authentication authentication
    ) {
        requireAdmin(authentication);
        return topUpService.get(requestId);
    }

    @GetMapping("/{requestId}/receipt")
    public ResponseEntity<ByteArrayResource> receipt(
            @PathVariable @Positive long requestId,
            Authentication authentication
    ) {
        requireAdmin(authentication);
        AttachmentDownload download = topUpService.receipt(requestId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(download.filename())
                        .build()
                        .toString())
                .body(new ByteArrayResource(download.bytes()));
    }

    @PostMapping("/{requestId}/approve")
    public AdminTopUpRequestDto approve(
            @PathVariable @Positive long requestId,
            @Valid @RequestBody(required = false) AdminTopUpReviewRequestDto request,
            Authentication authentication
    ) {
        AuthenticatedUser admin = requireAdmin(authentication);
        return topUpService.approve(requestId, admin.username(), request);
    }

    @PostMapping("/{requestId}/reject")
    public AdminTopUpRequestDto reject(
            @PathVariable @Positive long requestId,
            @Valid @RequestBody AdminTopUpRejectRequestDto request,
            Authentication authentication
    ) {
        AuthenticatedUser admin = requireAdmin(authentication);
        return topUpService.reject(requestId, admin.username(), request);
    }

    private AuthenticatedUser requireAdmin(Authentication authentication) {
        return authenticationService.requireUser(authentication, AuthUserType.ADMIN);
    }
}

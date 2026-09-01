package az.turn.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
@RequestMapping("/api/admin/support-requests")
public class AdminUserSupportRequestController {
    private final UserSupportRequestService supportService;
    private final RequestAuthenticationService authenticationService;

    public AdminUserSupportRequestController(UserSupportRequestService supportService, RequestAuthenticationService authenticationService) {
        this.supportService = supportService;
        this.authenticationService = authenticationService;
    }

    @GetMapping
    public AdminSupportRequestPageDto list(
            @RequestParam(required = false) UserSupportRequestType requestType,
            @RequestParam(required = false) SupportRequestStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        requireAdmin(authentication);
        return supportService.listForAdmin(requestType, status, page, size);
    }

    @GetMapping("/{requestId}")
    public AdminSupportRequestDto get(@PathVariable long requestId, Authentication authentication) {
        requireAdmin(authentication);
        return supportService.getForAdmin(requestId);
    }

    @GetMapping("/{requestId}/attachment")
    public ResponseEntity<ByteArrayResource> attachment(@PathVariable long requestId, Authentication authentication) {
        requireAdmin(authentication);
        AttachmentDownload download = supportService.downloadAttachment(requestId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(download.filename()).build().toString())
                .body(new ByteArrayResource(download.bytes()));
    }

    @PostMapping("/{requestId}/review")
    public AdminSupportRequestDto review(
            @PathVariable long requestId,
            @Valid @RequestBody AdminSupportRequestReviewRequestDto request,
            Authentication authentication
    ) {
        AuthenticatedUser admin = requireAdmin(authentication);
        return supportService.review(requestId, admin.username(), request);
    }

    private AuthenticatedUser requireAdmin(Authentication authentication) {
        return authenticationService.requireUser(authentication, AuthUserType.ADMIN);
    }
}

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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Validated
@RequestMapping("/api/users/me/support-requests")
public class UserSupportRequestController {
    private final UserSupportRequestService supportService;
    private final RequestAuthenticationService authenticationService;

    public UserSupportRequestController(UserSupportRequestService supportService, RequestAuthenticationService authenticationService) {
        this.supportService = supportService;
        this.authenticationService = authenticationService;
    }

    @PostMapping
    public UserSupportRequestDto create(@Valid @RequestBody UserSupportRequestCreateRequestDto request, Authentication authentication) {
        return supportService.create(userId(authentication), request);
    }

    @GetMapping
    public UserSupportRequestPageDto<UserSupportRequestDto> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        var result = supportService.mine(userId(authentication), page, size);
        return new UserSupportRequestPageDto<>(result.getContent(), result.getNumber(), result.getSize(), result.hasNext());
    }

    @GetMapping("/{requestId}")
    public UserSupportRequestDto get(@PathVariable long requestId, Authentication authentication) {
        return supportService.mineDetail(userId(authentication), requestId);
    }

    @PostMapping(value = "/{requestId}/attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserSupportRequestDto upload(
            @PathVariable long requestId,
            @RequestPart("file") MultipartFile file,
            Authentication authentication
    ) {
        return supportService.uploadAttachment(userId(authentication), requestId, file);
    }

    @GetMapping("/{requestId}/attachment")
    public ResponseEntity<ByteArrayResource> attachment(@PathVariable long requestId, Authentication authentication) {
        AttachmentDownload download = supportService.downloadUserAttachment(userId(authentication), requestId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(download.filename()).build().toString())
                .body(new ByteArrayResource(download.bytes()));
    }

    private long userId(Authentication authentication) {
        return authenticationService.requireUser(authentication, AuthUserType.USER).userId();
    }
}

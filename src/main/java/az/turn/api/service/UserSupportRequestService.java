package az.turn.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class UserSupportRequestService {
    private final UserSupportRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final AdminAccountRepository adminAccountRepository;
    private final SecureAttachmentService attachmentService;
    private final PrivateAttachmentStorage attachmentStorage;
    private final SecureAttachmentRepository attachmentRepository;
    private final PlatformAuditService auditService;
    private final Clock clock;

    public UserSupportRequestService(
            UserSupportRequestRepository requestRepository,
            UserRepository userRepository,
            AdminAccountRepository adminAccountRepository,
            SecureAttachmentService attachmentService,
            PrivateAttachmentStorage attachmentStorage,
            SecureAttachmentRepository attachmentRepository,
            PlatformAuditService auditService,
            Clock clock
    ) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.adminAccountRepository = adminAccountRepository;
        this.attachmentService = attachmentService;
        this.attachmentStorage = attachmentStorage;
        this.attachmentRepository = attachmentRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public UserSupportRequestDto create(long userId, UserSupportRequestCreateRequestDto command) {
        UserEntity user = requireUser(userId);
        LocalDateTime now = LocalDateTime.now(clock);
        UserSupportRequestEntity request = requestRepository.saveAndFlush(
                new UserSupportRequestEntity(user, command.requestType(), command.message(), now)
        );
        auditService.record("USER", String.valueOf(userId), "SUPPORT_REQUEST_CREATED", "USER_SUPPORT_REQUEST", request.getId(),
                command.requestType().name());
        return mapUser(request);
    }

    @Transactional(readOnly = true)
    public Page<UserSupportRequestDto> mine(long userId, int page, int size) {
        return requestRepository.findMine(userId, pageRequest(page, size)).map(this::mapUser);
    }

    @Transactional(readOnly = true)
    public UserSupportRequestDto mineDetail(long userId, long requestId) {
        return mapUser(requestRepository.findByIdAndUserId(requestId, userId)
                .orElseThrow(() -> notFound("Müraciət tapılmadı.")));
    }

    @Transactional
    public UserSupportRequestDto uploadAttachment(long userId, long requestId, MultipartFile file) {
        UserSupportRequestEntity request = requestRepository.findByIdForUpdate(requestId)
                .filter(value -> value.getUser().getId().equals(userId))
                .orElseThrow(() -> notFound("Müraciət tapılmadı."));
        if (request.getAttachment() != null) {
            throw conflict("Müraciətə artıq şəkil əlavə edilib.");
        }
        if (request.getStatus() != SupportRequestStatus.OPEN && request.getStatus() != SupportRequestStatus.IN_REVIEW) {
            throw conflict("Yekunlaşdırılmış müraciətə şəkil əlavə edilə bilməz.");
        }
        SecureUploadCommand command = multipartCommand(file);
        StoredSecureAttachment stored = attachmentService.storeImage(userId, SecureAttachmentPurpose.SUPPORT_REQUEST, command);
        SecureAttachmentEntity attachment = attachmentRepository.findById(stored.id())
                .orElseThrow(() -> notFound("Şəkil əlavəsi tapılmadı."));
        try {
            request.attach(attachment, LocalDateTime.now(clock));
            requestRepository.saveAndFlush(request);
        } catch (RuntimeException exception) {
            removeAttachment(attachment, exception);
            throw exception;
        }
        auditService.record("USER", String.valueOf(userId), "SUPPORT_REQUEST_ATTACHMENT_UPLOADED", "USER_SUPPORT_REQUEST", requestId,
                String.valueOf(stored.id()));
        return mapUser(request);
    }

    @Transactional(readOnly = true)
    public AdminSupportRequestPageDto listForAdmin(UserSupportRequestType requestType, SupportRequestStatus status, int page, int size) {
        Page<AdminSupportRequestDto> result = requestRepository.searchForAdmin(requestType, status, pageRequest(page, size))
                .map(this::mapAdmin);
        return new AdminSupportRequestPageDto(result.getContent(), result.getNumber(), result.getSize(), result.hasNext());
    }

    @Transactional(readOnly = true)
    public AdminSupportRequestDto getForAdmin(long requestId) {
        return mapAdmin(requestRepository.findById(requestId)
                .orElseThrow(() -> notFound("Müraciət tapılmadı.")));
    }

    @Transactional(readOnly = true)
    public AttachmentDownload downloadAttachment(long requestId) {
        UserSupportRequestEntity request = requestRepository.findById(requestId)
                .orElseThrow(() -> notFound("Müraciət tapılmadı."));
        return readAttachment(request);
    }

    @Transactional(readOnly = true)
    public AttachmentDownload downloadUserAttachment(long userId, long requestId) {
        UserSupportRequestEntity request = requestRepository.findByIdAndUserId(requestId, userId)
                .orElseThrow(() -> notFound("Müraciət tapılmadı."));
        return readAttachment(request);
    }

    private AttachmentDownload readAttachment(UserSupportRequestEntity request) {
        SecureAttachmentEntity attachment = request.getAttachment();
        if (attachment == null || attachment.getPurpose() != SecureAttachmentPurpose.SUPPORT_REQUEST) {
            throw notFound("Müraciətin şəkil əlavəsi yoxdur.");
        }
        return new AttachmentDownload(
                attachmentStorage.read(attachment.getStorageKey()),
                attachment.getMediaType(),
                attachment.getOriginalFilename()
        );
    }

    @Transactional
    public AdminSupportRequestDto review(long requestId, String adminUsername, AdminSupportRequestReviewRequestDto command) {
        AdminAccountEntity admin = adminAccountRepository.findByUsernameForUpdate(adminUsername)
                .orElseThrow(() -> notFound("Admin hesabı tapılmadı."));
        UserSupportRequestEntity request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> notFound("Müraciət tapılmadı."));
        try {
            request.review(admin, command.status(), command.response(), LocalDateTime.now(clock));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw conflict(exception.getMessage());
        }
        UserSupportRequestEntity saved = requestRepository.saveAndFlush(request);
        auditService.record("ADMIN", adminUsername, "SUPPORT_REQUEST_REVIEWED", "USER_SUPPORT_REQUEST", requestId,
                saved.getStatus().name());
        return mapAdmin(saved);
    }

    private SecureUploadCommand multipartCommand(MultipartFile file) {
        if (file == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Şəkil faylı tələb olunur.");
        }
        try {
            return new SecureUploadCommand(file.getOriginalFilename(), file.getContentType(), file.getSize(), file.getInputStream());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Şəkil faylı oxuna bilmədi.", exception);
        }
    }

    private void removeAttachment(SecureAttachmentEntity attachment, RuntimeException failure) {
        try {
            attachmentStorage.deleteIfExists(attachment.getStorageKey());
            attachmentRepository.delete(attachment);
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Səhifə parametrləri düzgün deyil.");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
    }

    private UserEntity requireUser(long userId) {
        return userRepository.findById(userId).orElseThrow(() -> notFound("İstifadəçi tapılmadı."));
    }

    private UserSupportRequestDto mapUser(UserSupportRequestEntity request) {
        SecureAttachmentEntity attachment = request.getAttachment();
        return new UserSupportRequestDto(
                request.getId(), request.getRequestType(), request.getMessage(), request.getStatus(), attachment != null,
                attachment == null ? null : attachment.getMediaType(), attachment == null ? null : attachment.getSizeBytes(),
                request.getAdminResponse(), request.getReviewedByAdmin() == null ? null : request.getReviewedByAdmin().getUsername(),
                request.getCreatedAt(), request.getUpdatedAt(), request.getReviewedAt()
        );
    }

    private AdminSupportRequestDto mapAdmin(UserSupportRequestEntity request) {
        UserEntity user = request.getUser();
        SecureAttachmentEntity attachment = request.getAttachment();
        return new AdminSupportRequestDto(
                request.getId(), user.getId(), user.getFirstName(), user.getLastName(), user.getNormalizedPhone(),
                request.getRequestType(), request.getMessage(), request.getStatus(), attachment == null ? null : attachment.getId(),
                attachment == null ? null : attachment.getMediaType(), attachment == null ? null : attachment.getSizeBytes(),
                attachment == null ? null : attachment.getOriginalFilename(), request.getAdminResponse(),
                request.getReviewedByAdmin() == null ? null : request.getReviewedByAdmin().getUsername(),
                request.getCreatedAt(), request.getUpdatedAt(), request.getReviewedAt()
        );
    }

    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}

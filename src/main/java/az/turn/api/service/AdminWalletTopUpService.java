package az.turn.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class AdminWalletTopUpService {
    private static final List<WalletTopUpRequestStatus> REVIEW_REQUIRED_STATUSES = List.of(
            WalletTopUpRequestStatus.PENDING_REVIEW,
            WalletTopUpRequestStatus.MANUAL_REVIEW,
            WalletTopUpRequestStatus.AUTO_CREDITED_PENDING_REVIEW
    );

    private final WalletTopUpRequestRepository requestRepository;
    private final AdminAccountRepository adminRepository;
    private final WalletAccountRepository walletAccountRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletTransactionService walletTransactionService;
    private final PrivateAttachmentStorage attachmentStorage;
    private final AdminTopUpRequestMapper mapper;
    private final PlatformAuditService auditService;
    private final Clock clock;

    public AdminWalletTopUpService(
            WalletTopUpRequestRepository requestRepository,
            AdminAccountRepository adminRepository,
            WalletAccountRepository walletAccountRepository,
            WalletTransactionRepository walletTransactionRepository,
            WalletTransactionService walletTransactionService,
            PrivateAttachmentStorage attachmentStorage,
            AdminTopUpRequestMapper mapper,
            PlatformAuditService auditService,
            Clock clock
    ) {
        this.requestRepository = requestRepository;
        this.adminRepository = adminRepository;
        this.walletAccountRepository = walletAccountRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.walletTransactionService = walletTransactionService;
        this.attachmentStorage = attachmentStorage;
        this.mapper = mapper;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminTopUpRequestPageDto list(String suppliedStatus, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Slice<WalletTopUpRequestEntity> result;
        if (isReviewRequired(suppliedStatus)) {
            result = requestRepository.findByStatusInOrderByReceiptUploadedAtAscIdAsc(
                    REVIEW_REQUIRED_STATUSES,
                    pageable
            );
        } else if (suppliedStatus == null || suppliedStatus.isBlank()) {
            result = requestRepository.findAllByOrderByCreatedAtAscIdAsc(pageable);
        } else {
            WalletTopUpRequestStatus status = parseStatus(suppliedStatus);
            result = requestRepository.findByStatusOrderByReceiptUploadedAtAscIdAsc(status, pageable);
        }
        return new AdminTopUpRequestPageDto(
                result.getContent().stream().map(mapper::toDto).toList(),
                result.getNumber(),
                result.getSize(),
                result.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public AdminTopUpRequestDto get(long requestId) {
        return mapper.toDto(requireRequest(requestId));
    }

    @Transactional(readOnly = true)
    public AttachmentDownload receipt(long requestId) {
        WalletTopUpRequestEntity request = requireRequest(requestId);
        SecureAttachmentEntity attachment = request.getReceiptAttachment();
        if (attachment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bu sorğu üçün çek yüklənməyib.");
        }
        return new AttachmentDownload(
                attachmentStorage.read(attachment.getStorageKey()),
                attachment.getMediaType(),
                attachment.getOriginalFilename()
        );
    }

    @Transactional
    public AdminTopUpRequestDto approve(long requestId, String adminUsername, AdminTopUpReviewRequestDto suppliedNote) {
        AdminAccountEntity admin = requireAdmin(adminUsername);
        WalletTopUpRequestEntity request = lockedReviewRequest(requestId);
        if (request.getStatus() == WalletTopUpRequestStatus.AUTO_CREDITED_PENDING_REVIEW) {
            return verifyAutomaticCredit(request, admin, adminUsername);
        }
        String note = normalizeNote(suppliedNote == null ? null : suppliedNote.note());
        String reference = "top-up-request:" + requestId;
        walletTransactionService.apply(
                request.getUser().getId(),
                new WalletTransactionCommandDto(
                        WalletTransactionType.TOP_UP,
                        request.getCoinAmount(),
                        WalletActorType.ADMIN,
                        null,
                        adminUsername,
                        reference,
                        note == null ? "Balans artırma çeki təsdiqləndi." : note
                )
        );
        WalletAccountEntity wallet = walletAccountRepository.findByUserId(request.getUser().getId())
                .orElseThrow(() -> new IllegalStateException("İstifadəçinin balans hesabı yaradılmayıb."));
        WalletTransactionEntity transaction = walletTransactionRepository
                .findByWalletAccountIdAndReferenceKey(wallet.getId(), reference)
                .orElseThrow(() -> new IllegalStateException("Balans əməliyyatı saxlanılmadı."));
        LocalDateTime now = LocalDateTime.now(clock);
        request.approve(admin, transaction, now);
        requestRepository.saveAndFlush(request);
        auditService.record("ADMIN", adminUsername, "WALLET_TOP_UP_APPROVED", "WALLET_TOP_UP_REQUEST", requestId,
                "coins=" + request.getCoinAmount());
        return mapper.toDto(request);
    }

    @Transactional
    public AdminTopUpRequestDto reject(long requestId, String adminUsername, AdminTopUpRejectRequestDto suppliedRequest) {
        AdminAccountEntity admin = requireAdmin(adminUsername);
        WalletTopUpRequestEntity request = lockedManualRequest(requestId);
        String reason = normalizeRequiredReason(suppliedRequest.reason());
        LocalDateTime now = LocalDateTime.now(clock);
        request.reject(admin, reason, now);
        requestRepository.saveAndFlush(request);
        auditService.record("ADMIN", adminUsername, "WALLET_TOP_UP_REJECTED", "WALLET_TOP_UP_REQUEST", requestId,
                "reason=" + reason);
        return mapper.toDto(request);
    }

    private AdminTopUpRequestDto verifyAutomaticCredit(
            WalletTopUpRequestEntity request,
            AdminAccountEntity admin,
            String adminUsername
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        request.verify(admin, now);
        requestRepository.saveAndFlush(request);
        auditService.record("ADMIN", adminUsername, "WALLET_TOP_UP_VERIFIED", "WALLET_TOP_UP_REQUEST", request.getId(),
                "coins=" + request.getCoinAmount());
        return mapper.toDto(request);
    }

    private WalletTopUpRequestEntity lockedReviewRequest(long requestId) {
        WalletTopUpRequestEntity request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Balans artırma sorğusu tapılmadı."));
        if (!REVIEW_REQUIRED_STATUSES.contains(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Yalnız gözləyən çek sorğusu bağlana bilər.");
        }
        return request;
    }

    private WalletTopUpRequestEntity lockedManualRequest(long requestId) {
        WalletTopUpRequestEntity request = lockedReviewRequest(requestId);
        if (request.getStatus() == WalletTopUpRequestStatus.AUTO_CREDITED_PENDING_REVIEW) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Avtomatik əlavə edilmiş coin üçün fırıldaq yoxlamasından istifadə edin."
            );
        }
        return request;
    }

    private AdminAccountEntity requireAdmin(String username) {
        return adminRepository.findByUsernameForUpdate(username)
                .filter(AdminAccountEntity::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin hesabı aktiv deyil."));
    }

    private WalletTopUpRequestEntity requireRequest(long requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Balans artırma sorğusu tapılmadı."));
    }

    private boolean isReviewRequired(String value) {
        return value != null && value.trim().equalsIgnoreCase("REVIEW_REQUIRED");
    }

    private WalletTopUpRequestStatus parseStatus(String value) {
        try {
            return WalletTopUpRequestStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ödəniş sorğusunun statusu düzgün deyil.");
        }
    }

    private String normalizeNote(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private String normalizeRequiredReason(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rədd səbəbi mütləqdir.");
        }
        return normalized;
    }
}

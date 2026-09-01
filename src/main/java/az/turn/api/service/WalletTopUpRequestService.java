package az.turn.api;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class WalletTopUpRequestService {
    private final UserRepository userRepository;
    private final WalletTopUpPackageRepository packageRepository;
    private final WalletTopUpRequestRepository requestRepository;
    private final WalletTopUpRequestStateService stateService;
    private final SecureAttachmentService attachmentService;
    private final PrivateAttachmentStorage attachmentStorage;
    private final SecureAttachmentRepository attachmentRepository;
    private final Clock clock;

    public WalletTopUpRequestService(
            UserRepository userRepository,
            WalletTopUpPackageRepository packageRepository,
            WalletTopUpRequestRepository requestRepository,
            WalletTopUpRequestStateService stateService,
            SecureAttachmentService attachmentService,
            PrivateAttachmentStorage attachmentStorage,
            SecureAttachmentRepository attachmentRepository,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.packageRepository = packageRepository;
        this.requestRepository = requestRepository;
        this.stateService = stateService;
        this.attachmentService = attachmentService;
        this.attachmentStorage = attachmentStorage;
        this.attachmentRepository = attachmentRepository;
        this.clock = clock;
    }

    @Transactional
    public WalletTopUpRequestDto create(long userId, String packageCode) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new WalletTopUpException(
                        WalletTopUpFailure.REQUEST_NOT_FOUND,
                        "İstifadəçi tapılmadı."
                ));
        WalletTopUpPackageEntity topUpPackage = packageRepository.findById(normalizeCode(packageCode))
                .filter(WalletTopUpPackageEntity::isActive)
                .orElseThrow(() -> new WalletTopUpException(
                        WalletTopUpFailure.PACKAGE_NOT_FOUND,
                        "Seçilmiş balans paketi mövcud deyil."
                ));
        LocalDateTime now = LocalDateTime.now(clock);
        requestRepository.findActiveByUserIdForUpdate(userId).ifPresent(active -> {
            if (active.getStatus() == WalletTopUpRequestStatus.AWAITING_RECEIPT && active.expire(now)) {
                requestRepository.saveAndFlush(active);
            } else {
                throw new WalletTopUpException(
                        WalletTopUpFailure.ACTIVE_REQUEST_EXISTS,
                        "Əvvəlki balans artırma sorğusu tamamlanmalıdır."
                );
            }
        });
        try {
            return map(requestRepository.saveAndFlush(new WalletTopUpRequestEntity(user, topUpPackage, now)), now);
        } catch (DataIntegrityViolationException exception) {
            throw new WalletTopUpException(
                    WalletTopUpFailure.ACTIVE_REQUEST_EXISTS,
                    "Əvvəlki balans artırma sorğusu tamamlanmalıdır."
            );
        }
    }

    public WalletTopUpRequestDto active(long userId) {
        return map(stateService.active(userId), LocalDateTime.now(clock));
    }

    public WalletTopUpRequestDto uploadReceipt(long userId, long requestId, MultipartFile file) {
        stateService.beginReceiptUpload(userId, requestId);
        long attachmentId = storeReceipt(userId, file);
        try {
            WalletTopUpRequestEntity request = stateService.attachReceipt(
                    userId,
                    requestId,
                    attachmentId,
                    LocalDateTime.now(clock)
            );
            return map(request, LocalDateTime.now(clock));
        } catch (RuntimeException exception) {
            deleteAttachment(userId, attachmentId, exception);
            throw exception;
        }
    }

    private long storeReceipt(long userId, MultipartFile file) {
        if (file == null) {
            throw new SecureUploadException(SecureUploadFailure.EMPTY_FILE, "Çek faylı seçilməyib.");
        }
        try {
            return attachmentService.storeImage(
                    userId,
                    SecureAttachmentPurpose.PAYMENT_RECEIPT,
                    new SecureImageUploadCommand(
                            file.getOriginalFilename(),
                            file.getContentType(),
                            file.getSize(),
                            file.getInputStream()
                    )
            ).id();
        } catch (IOException exception) {
            throw new SecureUploadException(
                    SecureUploadFailure.INVALID_IMAGE,
                    "Çek faylı oxuna bilmədi.",
                    exception
            );
        }
    }

    private void deleteAttachment(long userId, long attachmentId, RuntimeException failure) {
        attachmentRepository.findByIdAndOwnerUserId(attachmentId, userId).ifPresent(attachment -> {
            try {
                attachmentStorage.deleteIfExists(attachment.getStorageKey());
                attachmentRepository.delete(attachment);
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        });
    }

    private WalletTopUpRequestDto map(WalletTopUpRequestEntity request, LocalDateTime now) {
        return new WalletTopUpRequestDto(
                request.getId(),
                request.getTopUpPackage().getCode(),
                request.getAmountAzn(),
                request.getCoinAmount(),
                request.getCurrency(),
                request.getPaymentUrl(),
                request.getStatus(),
                request.getClickedAt(),
                request.getReceiptDeadlineAt(),
                request.getReceiptUploadedAt(),
                request.isReceiptWindowOpen(now)
        );
    }

    private String normalizeCode(String packageCode) {
        return packageCode == null ? "" : packageCode.trim().toUpperCase(Locale.ROOT);
    }
}

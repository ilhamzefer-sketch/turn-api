package az.turn.api;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WalletTopUpRequestService {
    private final WalletTopUpCheckoutService checkoutService;
    private final WalletProperties walletProperties;
    private final WalletTopUpRequestStateService stateService;
    private final EpointWalletPaymentService epointPaymentService;
    private final SecureAttachmentService attachmentService;
    private final PrivateAttachmentStorage attachmentStorage;
    private final SecureAttachmentRepository attachmentRepository;
    private final Clock clock;

    public WalletTopUpRequestService(
            WalletTopUpCheckoutService checkoutService,
            WalletProperties walletProperties,
            WalletTopUpRequestStateService stateService,
            EpointWalletPaymentService epointPaymentService,
            SecureAttachmentService attachmentService,
            PrivateAttachmentStorage attachmentStorage,
            SecureAttachmentRepository attachmentRepository,
            Clock clock
    ) {
        this.checkoutService = checkoutService;
        this.walletProperties = walletProperties;
        this.stateService = stateService;
        this.epointPaymentService = epointPaymentService;
        this.attachmentService = attachmentService;
        this.attachmentStorage = attachmentStorage;
        this.attachmentRepository = attachmentRepository;
        this.clock = clock;
    }

    public WalletTopUpRequestDto create(long userId, String packageCode) {
        boolean external = epointPaymentService.isConfigured();
        if (!external && !walletProperties.manualTopUpEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Kartla ödəniş hazırda əlçatan deyil.");
        }
        WalletTopUpPreparationDto prepared;
        try {
            prepared = checkoutService.prepare(userId, packageCode, external);
        } catch (DataIntegrityViolationException exception) {
            throw new WalletTopUpException(WalletTopUpFailure.ACTIVE_REQUEST_EXISTS,
                    "Əvvəlki balans artırma sorğusu tamamlanmalıdır.");
        }
        return prepared.dispatch() ? epointPaymentService.start(prepared.request()) : prepared.request();
    }

    @Transactional(noRollbackFor = WalletTopUpException.class)
    public WalletTopUpRequestDto active(long userId) {
        return WalletTopUpRequestMapper.map(stateService.active(userId), LocalDateTime.now(clock));
    }

    public WalletTopUpRequestDto get(long userId, long requestId) {
        return checkoutService.get(userId, requestId);
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
            return WalletTopUpRequestMapper.map(request, LocalDateTime.now(clock));
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
            return attachmentService.storePaymentReceipt(
                    userId,
                    new SecureUploadCommand(
                            file.getOriginalFilename(),
                            file.getContentType(),
                            file.getSize(),
                            file.getInputStream()
                    )
            ).id();
        } catch (IOException exception) {
            throw new SecureUploadException(
                    SecureUploadFailure.INVALID_FILE,
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

}

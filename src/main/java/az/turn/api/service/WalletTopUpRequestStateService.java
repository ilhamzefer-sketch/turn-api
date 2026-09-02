package az.turn.api;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class WalletTopUpRequestStateService {
    private final WalletTopUpRequestRepository requestRepository;
    private final SecureAttachmentRepository attachmentRepository;
    private final UserRepository userRepository;
    private final WalletTopUpCreditService creditService;
    private final Clock clock;

    public WalletTopUpRequestStateService(
            WalletTopUpRequestRepository requestRepository,
            SecureAttachmentRepository attachmentRepository,
            UserRepository userRepository,
            WalletTopUpCreditService creditService,
            Clock clock
    ) {
        this.requestRepository = requestRepository;
        this.attachmentRepository = attachmentRepository;
        this.userRepository = userRepository;
        this.creditService = creditService;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = WalletTopUpException.class)
    public WalletTopUpRequestEntity beginReceiptUpload(long userId, long requestId) {
        WalletTopUpRequestEntity request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new WalletTopUpException(
                        WalletTopUpFailure.REQUEST_NOT_FOUND,
                        "Balans artırma sorğusu tapılmadı."
                ));
        requireOwner(request, userId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (request.getStatus() == WalletTopUpRequestStatus.AWAITING_RECEIPT && !request.isReceiptWindowOpen(now)) {
            request.expire(now);
            requestRepository.saveAndFlush(request);
            throw new WalletTopUpException(
                    WalletTopUpFailure.RECEIPT_WINDOW_EXPIRED,
                    "Çek yükləmə müddəti bitib."
            );
        }
        if (request.getStatus() != WalletTopUpRequestStatus.AWAITING_RECEIPT) {
            throw new WalletTopUpException(
                    WalletTopUpFailure.RECEIPT_ALREADY_SUBMITTED,
                    "Bu ödəniş üçün çek artıq göndərilib və ya sorğu bağlanıb."
            );
        }
        return request;
    }

    @Transactional(noRollbackFor = WalletTopUpException.class)
    public WalletTopUpRequestEntity attachReceipt(
            long userId,
            long requestId,
            long attachmentId,
            LocalDateTime uploadedAt
    ) {
        WalletTopUpRequestEntity request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new WalletTopUpException(
                        WalletTopUpFailure.REQUEST_NOT_FOUND,
                        "Balans artırma sorğusu tapılmadı."
                ));
        requireOwner(request, userId);
        if (request.getStatus() != WalletTopUpRequestStatus.AWAITING_RECEIPT) {
            throw new WalletTopUpException(
                    WalletTopUpFailure.RECEIPT_ALREADY_SUBMITTED,
                    "Bu ödəniş üçün çek artıq göndərilib və ya sorğu bağlanıb."
            );
        }
        if (!request.isReceiptWindowOpen(uploadedAt)) {
            request.expire(uploadedAt);
            requestRepository.saveAndFlush(request);
            throw new WalletTopUpException(
                    WalletTopUpFailure.RECEIPT_WINDOW_EXPIRED,
                    "Çek yükləmə müddəti bitib."
            );
        }
        SecureAttachmentEntity attachment = attachmentRepository.findByIdAndOwnerUserId(attachmentId, userId)
                .orElseThrow(() -> new WalletTopUpException(
                        WalletTopUpFailure.ATTACHMENT_NOT_FOUND,
                        "Yüklənmiş çek tapılmadı."
                ));
        if (attachment.getPurpose() != SecureAttachmentPurpose.PAYMENT_RECEIPT) {
            throw new WalletTopUpException(
                    WalletTopUpFailure.ATTACHMENT_NOT_FOUND,
                    "Yüklənmiş fayl ödəniş çeki kimi istifadə edilə bilməz."
            );
        }
        UserEntity user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new WalletTopUpException(
                        WalletTopUpFailure.REQUEST_NOT_FOUND,
                        "İstifadəçi tapılmadı."
                ));
        if (user.requiresManualWalletTopUpReview()) {
            request.submitReceiptForManualReview(attachment, uploadedAt);
        } else {
            WalletTransactionEntity transaction = creditService.credit(request);
            request.submitReceiptWithAutomaticCredit(attachment, transaction, uploadedAt);
        }
        return requestRepository.saveAndFlush(request);
    }

    @Transactional(noRollbackFor = WalletTopUpException.class)
    public WalletTopUpRequestEntity active(long userId) {
        return requestRepository.findActiveByUserIdForUpdate(userId)
                .map(request -> expireIfNeeded(request, LocalDateTime.now(clock)))
                .orElseThrow(() -> new WalletTopUpException(
                        WalletTopUpFailure.REQUEST_NOT_FOUND,
                        "Aktiv balans artırma sorğusu yoxdur."
                ));
    }

    private WalletTopUpRequestEntity expireIfNeeded(WalletTopUpRequestEntity request, LocalDateTime now) {
        if (request.getStatus() == WalletTopUpRequestStatus.AWAITING_RECEIPT && !request.isReceiptWindowOpen(now)) {
            request.expire(now);
            requestRepository.saveAndFlush(request);
            throw new WalletTopUpException(
                    WalletTopUpFailure.REQUEST_NOT_FOUND,
                    "Aktiv balans artırma sorğusu yoxdur."
            );
        }
        return request;
    }

    private void requireOwner(WalletTopUpRequestEntity request, long userId) {
        if (request.getUser().getId() == null || !request.getUser().getId().equals(userId)) {
            throw new WalletTopUpException(
                    WalletTopUpFailure.REQUEST_NOT_FOUND,
                    "Balans artırma sorğusu tapılmadı."
            );
        }
    }
}

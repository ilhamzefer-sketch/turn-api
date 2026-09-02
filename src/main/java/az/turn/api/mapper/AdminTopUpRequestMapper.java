package az.turn.api;

import org.springframework.stereotype.Component;

@Component
public class AdminTopUpRequestMapper {
    public AdminTopUpRequestDto toDto(WalletTopUpRequestEntity request) {
        SecureAttachmentEntity attachment = request.getReceiptAttachment();
        UserEntity user = request.getUser();
        return new AdminTopUpRequestDto(
                request.getId(),
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getNormalizedPhone(),
                request.getTopUpPackage().getCode(),
                request.getAmountAzn(),
                request.getCoinAmount(),
                request.getCurrency(),
                request.getStatus().name(),
                request.getClickedAt(),
                request.getReceiptDeadlineAt(),
                request.getReceiptUploadedAt(),
                attachment == null ? null : attachment.getId(),
                attachment == null ? null : attachment.getMediaType(),
                attachment == null ? null : attachment.getSizeBytes(),
                user.getConfirmedWalletFraudCount(),
                request.getFraudCountAfter(),
                request.getReviewedAt(),
                request.getResolutionNote()
        );
    }
}

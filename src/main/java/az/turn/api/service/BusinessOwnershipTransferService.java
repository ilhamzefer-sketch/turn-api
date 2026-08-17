package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class BusinessOwnershipTransferService {
    private final BusinessOwnershipTransferRepository transferRepository;
    private final BusinessRepository businessRepository;
    private final BusinessMembershipRepository membershipRepository;
    private final ProviderAccessService accessService;
    private final PlatformAuditService auditService;
    private final Clock clock;

    public BusinessOwnershipTransferService(
            BusinessOwnershipTransferRepository transferRepository,
            BusinessRepository businessRepository,
            BusinessMembershipRepository membershipRepository,
            ProviderAccessService accessService,
            PlatformAuditService auditService,
            Clock clock
    ) {
        this.transferRepository = transferRepository;
        this.businessRepository = businessRepository;
        this.membershipRepository = membershipRepository;
        this.accessService = accessService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public OwnershipTransferResponseDto create(long businessId, long ownerUserId, long targetUserId) {
        BusinessEntity business = accessService.requirePrimaryOwner(businessId, ownerUserId);
        if (transferRepository.existsByBusinessIdAndStatus(businessId, OwnershipTransferStatus.PENDING_ACCEPTANCE)) {
            throw conflict("Biznes üçün artıq gözləyən sahiblik ötürməsi var.");
        }
        BusinessMembershipEntity target = membershipRepository.findByBusinessIdAndUserId(businessId, targetUserId)
                .orElseThrow(() -> conflict("Yeni sahib biznes administratoru olmalıdır."));
        if (target.getStatus() != BusinessMembershipStatus.ACTIVE || target.getRole() != BusinessRole.ADMIN) {
            throw conflict("Yeni sahib aktiv biznes administratoru olmalıdır.");
        }
        BusinessOwnershipTransferEntity transfer = new BusinessOwnershipTransferEntity();
        transfer.setBusiness(business);
        transfer.setFromOwnerUser(business.getPrimaryOwnerUser());
        transfer.setToAdminUser(target.getUser());
        return toDto(transferRepository.save(transfer));
    }

    @Transactional(readOnly = true)
    public List<OwnershipTransferResponseDto> invitations(long userId) {
        return transferRepository.findByToAdminUserIdAndStatusOrderByCreatedAtDesc(
                userId,
                OwnershipTransferStatus.PENDING_ACCEPTANCE
        ).stream().map(this::toDto).toList();
    }

    @Transactional
    public OwnershipTransferResponseDto respond(long transferId, long userId, boolean accept) {
        BusinessOwnershipTransferEntity transfer = transferRepository.findByIdAndToAdminUserId(transferId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sahiblik ötürməsi tapılmadı."));
        if (transfer.getStatus() != OwnershipTransferStatus.PENDING_ACCEPTANCE) {
            throw conflict("Sahiblik ötürməsi artıq cavablandırılıb.");
        }
        if (accept) accept(transfer);
        transfer.setStatus(accept ? OwnershipTransferStatus.ACCEPTED : OwnershipTransferStatus.REJECTED);
        transfer.setRespondedAt(LocalDateTime.now(clock));
        BusinessOwnershipTransferEntity saved = transferRepository.save(transfer);
        auditService.record(
                "USER", String.valueOf(userId), "RESPOND_OWNERSHIP_TRANSFER", "BUSINESS",
                transfer.getBusiness().getId(), saved.getStatus().name()
        );
        return toDto(saved);
    }

    private void accept(BusinessOwnershipTransferEntity transfer) {
        long businessId = transfer.getBusiness().getId();
        BusinessEntity business = businessRepository.findByIdForUpdate(businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Biznes tapılmadı."));
        if (!business.getPrimaryOwnerUser().getId().equals(transfer.getFromOwnerUser().getId())) {
            throw conflict("Biznesin əsas sahibi dəyişib. Bu ötürmə etibarsızdır.");
        }
        BusinessMembershipEntity oldOwner = membershipRepository.findByBusinessIdAndUserIdForUpdate(
                businessId,
                transfer.getFromOwnerUser().getId()
        ).orElseThrow(() -> conflict("Əvvəlki sahib üzvlüyü tapılmadı."));
        BusinessMembershipEntity newOwner = membershipRepository.findByBusinessIdAndUserIdForUpdate(
                businessId,
                transfer.getToAdminUser().getId()
        ).orElseThrow(() -> conflict("Yeni sahib üzvlüyü tapılmadı."));
        if (newOwner.getStatus() != BusinessMembershipStatus.ACTIVE || newOwner.getRole() != BusinessRole.ADMIN) {
            throw conflict("Yeni sahib artıq aktiv administrator deyil.");
        }
        oldOwner.setRole(BusinessRole.ADMIN);
        newOwner.setRole(BusinessRole.PRIMARY_OWNER);
        business.setPrimaryOwnerUser(transfer.getToAdminUser());
        membershipRepository.save(oldOwner);
        membershipRepository.save(newOwner);
        businessRepository.save(business);
    }

    private OwnershipTransferResponseDto toDto(BusinessOwnershipTransferEntity value) {
        return new OwnershipTransferResponseDto(
                value.getId(), value.getBusiness().getId(), value.getFromOwnerUser().getId(),
                value.getToAdminUser().getId(), value.getStatus(), value.getCreatedAt(), value.getRespondedAt()
        );
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}

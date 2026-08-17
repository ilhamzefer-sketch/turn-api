package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SupportRequestService {
    private static final List<SupportRequestStatus> OPEN_STATUSES = List.of(
            SupportRequestStatus.OPEN,
            SupportRequestStatus.IN_REVIEW
    );

    private final AccountOwnershipDisputeRepository disputeRepository;
    private final PhoneChangeRequestRepository phoneChangeRepository;
    private final AccountDeletionRequestRepository deletionRepository;
    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final PhoneNumberService phoneNumberService;
    private final UserSessionService userSessionService;
    private final PlatformAuditService auditService;
    private final Clock clock;

    public SupportRequestService(
            AccountOwnershipDisputeRepository disputeRepository,
            PhoneChangeRequestRepository phoneChangeRepository,
            AccountDeletionRequestRepository deletionRepository,
            UserRepository userRepository,
            BusinessRepository businessRepository,
            PhoneNumberService phoneNumberService,
            UserSessionService userSessionService,
            PlatformAuditService auditService,
            Clock clock
    ) {
        this.disputeRepository = disputeRepository;
        this.phoneChangeRepository = phoneChangeRepository;
        this.deletionRepository = deletionRepository;
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.phoneNumberService = phoneNumberService;
        this.userSessionService = userSessionService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public OwnershipDisputeDto createDispute(OwnershipDisputeCreateRequestDto request) {
        String disputedPhone = phoneNumberService.normalizeAzerbaijaniPhone(request.disputedPhone());
        AccountOwnershipDisputeEntity dispute = new AccountOwnershipDisputeEntity();
        dispute.setDisputedPhone(disputedPhone);
        dispute.setDisputedUser(userRepository.findByNormalizedPhone(disputedPhone).orElse(null));
        dispute.setClaimantName(request.claimantName().trim());
        dispute.setClaimantContactPhone(phoneNumberService.normalizeAzerbaijaniPhone(request.claimantContactPhone()));
        dispute.setDescription(request.description().trim());
        return disputeDto(disputeRepository.save(dispute));
    }

    @Transactional
    public PhoneChangeRequestDto createPhoneChange(long userId, PhoneChangeCreateRequestDto request) {
        UserEntity user = requireUser(userId);
        if (phoneChangeRepository.existsByUserIdAndStatusIn(userId, OPEN_STATUSES)) {
            throw conflict("Artıq açıq telefon dəyişmə müraciətiniz var.");
        }
        String requestedPhone = phoneNumberService.normalizeAzerbaijaniPhone(request.requestedPhone());
        if (requestedPhone.equals(user.getNormalizedPhone())) throw conflict("Yeni telefon mövcud telefonla eynidir.");
        if (userRepository.existsByNormalizedPhone(requestedPhone)) throw conflict("Bu telefon artıq başqa hesaba aiddir.");
        PhoneChangeRequestEntity change = new PhoneChangeRequestEntity();
        change.setUser(user);
        change.setCurrentNormalizedPhone(user.getNormalizedPhone());
        change.setRequestedNormalizedPhone(requestedPhone);
        change.setReason(request.reason().trim());
        return phoneDto(phoneChangeRepository.save(change));
    }

    @Transactional
    public AccountDeletionRequestDto createDeletion(long userId) {
        UserEntity user = requireUser(userId);
        if (deletionRepository.existsByUserIdAndStatusIn(userId, OPEN_STATUSES)) {
            throw conflict("Artıq açıq hesab silmə müraciətiniz var.");
        }
        AccountDeletionRequestEntity deletion = new AccountDeletionRequestEntity();
        deletion.setUser(user);
        return deletionDto(deletionRepository.save(deletion));
    }

    @Transactional(readOnly = true)
    public List<OwnershipDisputeDto> disputes() {
        return disputeRepository.findAllByOrderByCreatedAtDesc().stream().map(this::disputeDto).toList();
    }

    @Transactional(readOnly = true)
    public List<PhoneChangeRequestDto> phoneChanges() {
        return phoneChangeRepository.findAllByOrderByCreatedAtDesc().stream().map(this::phoneDto).toList();
    }

    @Transactional(readOnly = true)
    public List<AccountDeletionRequestDto> deletions() {
        return deletionRepository.findAllByOrderByRequestedAtDesc().stream().map(this::deletionDto).toList();
    }

    @Transactional
    public OwnershipDisputeDto resolveDispute(
            long id,
            String admin,
            OwnershipDisputeResolveRequestDto request
    ) {
        AccountOwnershipDisputeEntity dispute = disputeRepository.findById(id)
                .orElseThrow(() -> notFound("Müraciət tapılmadı."));
        requireOpen(dispute.getStatus());
        UserEntity user = dispute.getDisputedUser();
        if (request.action() != DisputeResolutionAction.NO_ACTION && user == null) {
            throw conflict("Bu müraciət tanınmış istifadəçi hesabına bağlı deyil.");
        }
        applyDisputeAction(user, request.action(), dispute);
        dispute.setStatus(request.reject() ? SupportRequestStatus.REJECTED : SupportRequestStatus.RESOLVED);
        dispute.setResolutionAction(request.action());
        dispute.setResolutionNote(request.resolutionNote().trim());
        dispute.setReviewedByAdmin(admin);
        dispute.setResolvedAt(LocalDateTime.now(clock));
        AccountOwnershipDisputeEntity saved = disputeRepository.save(dispute);
        auditService.record("ADMIN", admin, "RESOLVE_DISPUTE", "ACCOUNT_DISPUTE", id, request.action().name());
        return disputeDto(saved);
    }

    @Transactional
    public PhoneChangeRequestDto resolvePhoneChange(
            long id,
            String admin,
            SupportResolutionRequestDto request
    ) {
        PhoneChangeRequestEntity change = phoneChangeRepository.findById(id)
                .orElseThrow(() -> notFound("Telefon dəyişmə müraciəti tapılmadı."));
        requireOpen(change.getStatus());
        if (request.approve()) {
            if (userRepository.existsByNormalizedPhone(change.getRequestedNormalizedPhone())) {
                throw conflict("Tələb edilən telefon artıq başqa hesaba aiddir.");
            }
            UserEntity user = change.getUser();
            user.setNormalizedPhone(change.getRequestedNormalizedPhone());
            userRepository.save(user);
            userSessionService.revokeAllSessions(user.getId());
            change.setStatus(SupportRequestStatus.APPROVED);
        } else {
            change.setStatus(SupportRequestStatus.REJECTED);
        }
        change.setReviewedByAdmin(admin);
        change.setResolutionNote(request.resolutionNote().trim());
        change.setResolvedAt(LocalDateTime.now(clock));
        PhoneChangeRequestEntity saved = phoneChangeRepository.save(change);
        auditService.record("ADMIN", admin, "RESOLVE_PHONE_CHANGE", "PHONE_CHANGE", id, saved.getStatus().name());
        return phoneDto(saved);
    }

    @Transactional
    public AccountDeletionRequestDto resolveDeletion(
            long id,
            String admin,
            SupportResolutionRequestDto request
    ) {
        AccountDeletionRequestEntity deletion = deletionRepository.findById(id)
                .orElseThrow(() -> notFound("Hesab silmə müraciəti tapılmadı."));
        requireOpen(deletion.getStatus());
        UserEntity user = deletion.getUser();
        if (request.approve()) {
            if (businessRepository.existsByPrimaryOwnerUserIdAndStatus(user.getId(), ProviderStatus.ACTIVE)) {
                throw conflict("Aktiv biznes sahibliyi əvvəlcə başqa administratora ötürülməlidir.");
            }
            anonymize(user);
            deletion.setStatus(SupportRequestStatus.COMPLETED);
        } else {
            deletion.setStatus(SupportRequestStatus.REJECTED);
        }
        deletion.setProcessedByAdmin(admin);
        deletion.setProcessedAt(LocalDateTime.now(clock));
        deletion.setResolutionNote(request.resolutionNote().trim());
        AccountDeletionRequestEntity saved = deletionRepository.save(deletion);
        auditService.record("ADMIN", admin, "RESOLVE_ACCOUNT_DELETION", "ACCOUNT_DELETION", id, saved.getStatus().name());
        return deletionDto(saved);
    }

    @Transactional
    public void unlock(long userId, String admin) {
        UserEntity user = requireUser(userId);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        auditService.record("ADMIN", admin, "UNLOCK_ACCOUNT", "USER", userId, null);
    }

    private void applyDisputeAction(
            UserEntity user,
            DisputeResolutionAction action,
            AccountOwnershipDisputeEntity dispute
    ) {
        if (action == DisputeResolutionAction.NO_ACTION) return;
        if (action == DisputeResolutionAction.SUSPEND) user.setStatus(UserStatus.SUSPENDED);
        if (action == DisputeResolutionAction.RESET_PASSWORD) {
            user.setPasswordHash(null);
            user.setStatus(UserStatus.PASSWORD_RESET_REQUIRED);
            dispute.setPasswordResetRequiredAt(LocalDateTime.now(clock));
        }
        if (action == DisputeResolutionAction.RESTORE_ACCESS) {
            if (user.getPasswordHash() == null) throw conflict("Şifrəsiz hesab bərpa edilə bilməz; password reset seçin.");
            user.setStatus(UserStatus.ACTIVE);
        }
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        userSessionService.revokeAllSessions(user.getId());
    }

    private void anonymize(UserEntity user) {
        userSessionService.revokeAllSessions(user.getId());
        user.setFirstName("Deleted");
        user.setLastName("User");
        user.setNormalizedPhone(null);
        user.setPasswordHash(null);
        user.setStatus(UserStatus.ANONYMIZED);
        user.setInvitedFirstName(null);
        user.setInvitedLastName(null);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
    }

    private UserEntity requireUser(long id) {
        return userRepository.findById(id).orElseThrow(() -> notFound("İstifadəçi tapılmadı."));
    }

    private void requireOpen(SupportRequestStatus status) {
        if (!OPEN_STATUSES.contains(status)) throw conflict("Müraciət artıq yekunlaşdırılıb.");
    }

    private OwnershipDisputeDto disputeDto(AccountOwnershipDisputeEntity value) {
        return new OwnershipDisputeDto(
                value.getId(), value.getDisputedUser() == null ? null : value.getDisputedUser().getId(),
                value.getDisputedPhone(), value.getClaimantName(), value.getClaimantContactPhone(),
                value.getDescription(), value.getStatus(), value.getResolutionAction(), value.getResolutionNote(),
                value.getReviewedByAdmin(), value.getCreatedAt(), value.getResolvedAt()
        );
    }

    private PhoneChangeRequestDto phoneDto(PhoneChangeRequestEntity value) {
        return new PhoneChangeRequestDto(
                value.getId(), value.getUser().getId(), value.getCurrentNormalizedPhone(),
                value.getRequestedNormalizedPhone(), value.getReason(), value.getStatus(),
                value.getResolutionNote(), value.getCreatedAt(), value.getResolvedAt()
        );
    }

    private AccountDeletionRequestDto deletionDto(AccountDeletionRequestEntity value) {
        return new AccountDeletionRequestDto(
                value.getId(), value.getUser().getId(), value.getStatus(), value.getResolutionNote(),
                value.getRequestedAt(), value.getProcessedAt()
        );
    }

    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}

package az.turn.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class BusinessMembershipService {
    private final BusinessMembershipRepository membershipRepository;
    private final RoomAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final ProviderAccessService accessService;
    private final ProviderInputService inputService;
    private final PhoneNumberService phoneNumberService;
    private final ProviderWorkspaceMapper mapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final int businessDailyLimit;
    private final int administratorDailyLimit;
    private final int administratorBurstLimit;

    public BusinessMembershipService(
            BusinessMembershipRepository membershipRepository,
            RoomAssignmentRepository assignmentRepository,
            UserRepository userRepository,
            ProviderAccessService accessService,
            ProviderInputService inputService,
            PhoneNumberService phoneNumberService,
            ProviderWorkspaceMapper mapper,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            @Value("${app.limits.pending-accounts.business-daily:500}") int businessDailyLimit,
            @Value("${app.limits.pending-accounts.administrator-daily:100}") int administratorDailyLimit,
            @Value("${app.limits.pending-accounts.administrator-per-minute:20}") int administratorBurstLimit
    ) {
        this.membershipRepository = membershipRepository;
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.accessService = accessService;
        this.inputService = inputService;
        this.phoneNumberService = phoneNumberService;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.businessDailyLimit = businessDailyLimit;
        this.administratorDailyLimit = administratorDailyLimit;
        this.administratorBurstLimit = administratorBurstLimit;
    }

    @Transactional
    public BusinessMembershipDto invite(
            long businessId,
            long actorUserId,
            BusinessMemberInviteRequestDto request
    ) {
        BusinessEntity business = accessService.requireBusinessManager(businessId, actorUserId);
        UserEntity actor = accessService.requireActiveUser(actorUserId);
        validateInvitableRole(request.role());
        String phone = phoneNumberService.normalizeAzerbaijaniPhone(request.phone());
        UserEntity invitedUser = userRepository.findByNormalizedPhoneForUpdate(phone).orElse(null);
        boolean createdPendingUser = invitedUser == null;
        if (createdPendingUser) {
            validatePendingAccountLimits(businessId, actorUserId);
            invitedUser = createPendingUser(actor, phone, request);
        }
        if (invitedUser.getStatus() == UserStatus.ANONYMIZED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu telefon nömrəsi dəvət edilə bilməz.");
        }

        BusinessMembershipEntity membership = membershipRepository
                .findByBusinessIdAndUserId(businessId, invitedUser.getId())
                .orElseGet(BusinessMembershipEntity::new);
        if (membership.getId() != null && membership.getStatus() != BusinessMembershipStatus.REJECTED
                && membership.getStatus() != BusinessMembershipStatus.REMOVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu istifadəçi artıq biznesə əlavə edilib və ya dəvət gözləyir.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        membership.setBusiness(business);
        membership.setUser(invitedUser);
        membership.setRole(request.role());
        membership.setStatus(BusinessMembershipStatus.PENDING_ACCEPTANCE);
        membership.setInvitedByUser(actor);
        membership.setInvitedFirstName(resolveInvitationName(request.firstName(), invitedUser.getFirstName()));
        membership.setInvitedLastName(resolveInvitationName(request.lastName(), invitedUser.getLastName()));
        membership.setCreatedPendingUser(createdPendingUser);
        membership.setInvitedAt(now);
        membership.setAcceptedAt(null);
        membership.setRejectedAt(null);
        membership.setRemovedAt(null);
        BusinessMembershipEntity saved = membershipRepository.save(membership);
        eventPublisher.publishEvent(new BusinessInvitationCreatedEvent(
                saved.getId(),
                invitedUser.getNormalizedPhone(),
                business.getName()
        ));
        return mapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<BusinessMembershipDto> list(long businessId, long actorUserId) {
        accessService.requireBusinessManager(businessId, actorUserId);
        return membershipRepository.findByBusinessIdOrderByInvitedAtAsc(businessId)
                .stream().map(mapper::toDto).toList();
    }

    @Transactional
    public BusinessMembershipDto updateRole(
            long businessId,
            long membershipId,
            long actorUserId,
            BusinessMembershipUpdateRequestDto request
    ) {
        accessService.requireBusinessManager(businessId, actorUserId);
        validateInvitableRole(request.role());
        BusinessMembershipEntity membership = requireBusinessMembership(businessId, membershipId);
        if (membership.getRole() == BusinessRole.PRIMARY_OWNER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Əsas sahibin rolu bu əməliyyatla dəyişdirilə bilməz.");
        }
        membership.setRole(request.role());
        return mapper.toDto(membershipRepository.save(membership));
    }

    @Transactional
    public void remove(long businessId, long membershipId, long actorUserId) {
        accessService.requireBusinessManager(businessId, actorUserId);
        BusinessMembershipEntity membership = requireBusinessMembership(businessId, membershipId);
        if (membership.getRole() == BusinessRole.PRIMARY_OWNER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Əsas biznes sahibi silinə bilməz.");
        }
        membership.setStatus(BusinessMembershipStatus.REMOVED);
        membership.setRemovedAt(LocalDateTime.now(clock));
        membershipRepository.save(membership);
        assignmentRepository.findByUserIdAndRoomBranchBusinessIdAndStatusIn(
                membership.getUser().getId(),
                businessId,
                List.of(RoomAssignmentStatus.ACTIVE, RoomAssignmentStatus.PENDING_ACCEPTANCE)
        ).forEach(this::revokeAssignment);
    }

    @Transactional
    public BusinessMembershipDto accept(long membershipId, long userId) {
        UserEntity user = accessService.requireActiveUser(userId);
        BusinessMembershipEntity membership = requireUserInvitation(membershipId, userId);
        membership.setStatus(BusinessMembershipStatus.ACTIVE);
        membership.setAcceptedAt(LocalDateTime.now(clock));
        membership.setRejectedAt(null);
        membership.setUser(user);
        return mapper.toDto(membershipRepository.save(membership));
    }

    @Transactional
    public BusinessMembershipDto reject(long membershipId, long userId) {
        accessService.requireActiveUser(userId);
        BusinessMembershipEntity membership = requireUserInvitation(membershipId, userId);
        membership.setStatus(BusinessMembershipStatus.REJECTED);
        membership.setRejectedAt(LocalDateTime.now(clock));
        assignmentRepository.findByUserIdAndRoomBranchBusinessIdAndStatus(
                userId,
                membership.getBusiness().getId(),
                RoomAssignmentStatus.PENDING_ACCEPTANCE
        ).forEach(this::rejectAssignment);
        return mapper.toDto(membershipRepository.save(membership));
    }

    private UserEntity createPendingUser(
            UserEntity actor,
            String phone,
            BusinessMemberInviteRequestDto request
    ) {
        String firstName = inputService.required(request.firstName(), "Yeni istifadəçi üçün ad mütləqdir.");
        String lastName = inputService.required(request.lastName(), "Yeni istifadəçi üçün soyad mütləqdir.");
        UserEntity user = new UserEntity();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setInvitedFirstName(firstName);
        user.setInvitedLastName(lastName);
        user.setNormalizedPhone(phone);
        user.setStatus(UserStatus.PENDING);
        user.setCreatedByUser(actor);
        return userRepository.saveAndFlush(user);
    }

    private void validatePendingAccountLimits(long businessId, long actorUserId) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime dayStart = LocalDate.now(clock).atStartOfDay();
        if (membershipRepository.countByBusinessIdAndCreatedPendingUserTrueAndInvitedAtAfter(businessId, dayStart)
                >= businessDailyLimit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Biznes üçün gündəlik pending hesab limiti dolub.");
        }
        if (membershipRepository.countByInvitedByUserIdAndCreatedPendingUserTrueAndInvitedAtAfter(actorUserId, dayStart)
                >= administratorDailyLimit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "İdarəçi üçün gündəlik pending hesab limiti dolub.");
        }
        if (membershipRepository.countByInvitedByUserIdAndCreatedPendingUserTrueAndInvitedAtAfter(
                actorUserId,
                now.minusMinutes(1)
        ) >= administratorBurstLimit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Qısa müddətdə çox sayda pending hesab yaradılıb.");
        }
    }

    private BusinessMembershipEntity requireBusinessMembership(long businessId, long membershipId) {
        BusinessMembershipEntity membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Biznes üzvlüyü tapılmadı."));
        if (!membership.getBusiness().getId().equals(businessId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Biznes üzvlüyü tapılmadı.");
        }
        return membership;
    }

    private BusinessMembershipEntity requireUserInvitation(long membershipId, long userId) {
        BusinessMembershipEntity membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Biznes dəvəti tapılmadı."));
        if (!membership.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu dəvət sizə aid deyil.");
        }
        if (membership.getStatus() != BusinessMembershipStatus.PENDING_ACCEPTANCE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Biznes dəvəti artıq cavablandırılıb.");
        }
        return membership;
    }

    private void validateInvitableRole(BusinessRole role) {
        if (role == null || role == BusinessRole.PRIMARY_OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dəvət üçün ADMIN və ya EMPLOYEE rolu seçilməlidir.");
        }
    }

    private String resolveInvitationName(String requested, String fallback) {
        String value = inputService.optional(requested);
        return value == null ? fallback : value;
    }

    private void revokeAssignment(RoomAssignmentEntity assignment) {
        assignment.setStatus(RoomAssignmentStatus.REVOKED);
        assignment.setRevokedAt(LocalDateTime.now(clock));
        assignmentRepository.save(assignment);
    }

    private void rejectAssignment(RoomAssignmentEntity assignment) {
        assignment.setStatus(RoomAssignmentStatus.REJECTED);
        assignment.setRespondedAt(LocalDateTime.now(clock));
        assignmentRepository.save(assignment);
    }
}

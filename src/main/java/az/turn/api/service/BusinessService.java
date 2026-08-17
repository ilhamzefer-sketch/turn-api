package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class BusinessService {
    private final BusinessRepository businessRepository;
    private final BusinessMembershipRepository membershipRepository;
    private final ProviderAccessService accessService;
    private final ProviderInputService inputService;
    private final PhoneNumberService phoneNumberService;
    private final ProviderWorkspaceMapper mapper;
    private final Clock clock;

    public BusinessService(
            BusinessRepository businessRepository,
            BusinessMembershipRepository membershipRepository,
            ProviderAccessService accessService,
            ProviderInputService inputService,
            PhoneNumberService phoneNumberService,
            ProviderWorkspaceMapper mapper,
            Clock clock
    ) {
        this.businessRepository = businessRepository;
        this.membershipRepository = membershipRepository;
        this.accessService = accessService;
        this.inputService = inputService;
        this.phoneNumberService = phoneNumberService;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public BusinessResponseDto create(long userId, BusinessUpsertRequestDto request) {
        UserEntity owner = accessService.requireActiveUser(userId);
        BusinessEntity business = new BusinessEntity();
        apply(business, request);
        business.setPrimaryOwnerUser(owner);
        business.setStatus(ProviderStatus.ACTIVE);
        BusinessEntity saved = businessRepository.save(business);

        BusinessMembershipEntity membership = new BusinessMembershipEntity();
        membership.setBusiness(saved);
        membership.setUser(owner);
        membership.setRole(BusinessRole.PRIMARY_OWNER);
        membership.setStatus(BusinessMembershipStatus.ACTIVE);
        membership.setInvitedByUser(owner);
        membership.setInvitedFirstName(owner.getFirstName());
        membership.setInvitedLastName(owner.getLastName());
        LocalDateTime now = LocalDateTime.now(clock);
        membership.setInvitedAt(now);
        membership.setAcceptedAt(now);
        membershipRepository.save(membership);
        return mapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<BusinessResponseDto> getMine(long userId) {
        accessService.requireActiveUser(userId);
        return membershipRepository.findByUserIdAndStatusOrderByInvitedAtAsc(
                        userId,
                        BusinessMembershipStatus.ACTIVE
                ).stream()
                .map(BusinessMembershipEntity::getBusiness)
                .filter(business -> business.getStatus() == ProviderStatus.ACTIVE)
                .map(mapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public BusinessResponseDto get(long businessId, long userId) {
        return mapper.toDto(accessService.requireBusinessManager(businessId, userId));
    }

    @Transactional
    public BusinessResponseDto update(long businessId, long userId, BusinessUpsertRequestDto request) {
        BusinessEntity business = accessService.requireBusinessManager(businessId, userId);
        apply(business, request);
        return mapper.toDto(businessRepository.save(business));
    }

    @Transactional
    public void archive(long businessId, long userId) {
        BusinessEntity business = accessService.requirePrimaryOwner(businessId, userId);
        business.setStatus(ProviderStatus.ARCHIVED);
        business.setArchivedAt(LocalDateTime.now(clock));
        businessRepository.save(business);
    }

    private void apply(BusinessEntity business, BusinessUpsertRequestDto request) {
        business.setName(inputService.required(request.name(), "Biznes adı mütləqdir."));
        business.setLegalName(inputService.optional(request.legalName()));
        business.setDescription(inputService.optional(request.description()));
        business.setTaxId(inputService.optional(request.taxId()));
        business.setLogoUrl(inputService.optional(request.logoUrl()));
        business.setNormalizedPhone(phoneNumberService.normalizeAzerbaijaniPhone(request.phone()));
        business.setTimezone(inputService.timezone(request.timezone(), "Asia/Baku"));
    }
}

package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class BranchService {
    private final BranchRepository branchRepository;
    private final RoomRepository roomRepository;
    private final ProviderAccessService accessService;
    private final ProviderInputService inputService;
    private final PhoneNumberService phoneNumberService;
    private final ProviderWorkspaceMapper mapper;
    private final Clock clock;

    public BranchService(
            BranchRepository branchRepository,
            RoomRepository roomRepository,
            ProviderAccessService accessService,
            ProviderInputService inputService,
            PhoneNumberService phoneNumberService,
            ProviderWorkspaceMapper mapper,
            Clock clock
    ) {
        this.branchRepository = branchRepository;
        this.roomRepository = roomRepository;
        this.accessService = accessService;
        this.inputService = inputService;
        this.phoneNumberService = phoneNumberService;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public BranchResponseDto create(long businessId, long userId, BranchUpsertRequestDto request) {
        BusinessEntity business = accessService.requireBusinessManager(businessId, userId);
        BranchEntity branch = new BranchEntity();
        branch.setBusiness(business);
        branch.setStatus(ProviderStatus.ACTIVE);
        apply(branch, request, business.getTimezone());
        return mapper.toDto(branchRepository.save(branch));
    }

    @Transactional(readOnly = true)
    public List<BranchResponseDto> list(long businessId, long userId) {
        accessService.requireBusinessManager(businessId, userId);
        return branchRepository.findByBusinessIdOrderByCreatedAtAsc(businessId)
                .stream().map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public BranchResponseDto get(long branchId, long userId) {
        return mapper.toDto(accessService.requireManagedBranch(branchId, userId));
    }

    @Transactional
    public BranchResponseDto update(long branchId, long userId, BranchUpsertRequestDto request) {
        BranchEntity branch = accessService.requireManagedBranch(branchId, userId);
        apply(branch, request, branch.getBusiness().getTimezone());
        return mapper.toDto(branchRepository.save(branch));
    }

    @Transactional
    public void archive(long branchId, long userId) {
        BranchEntity branch = accessService.requireManagedBranch(branchId, userId);
        if (roomRepository.existsByBranchIdAndStatusNot(branchId, RoomStatus.ARCHIVED)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Filial arxivləşdirilməzdən əvvəl bütün otaqlar köçürülməli və ya arxivləşdirilməlidir."
            );
        }
        branch.setStatus(ProviderStatus.ARCHIVED);
        branch.setArchivedAt(LocalDateTime.now(clock));
        branchRepository.save(branch);
    }

    private void apply(BranchEntity branch, BranchUpsertRequestDto request, String businessTimezone) {
        branch.setName(inputService.required(request.name(), "Filial adı mütləqdir."));
        branch.setAddress(inputService.required(request.address(), "Filial ünvanı mütləqdir."));
        branch.setCity(inputService.required(request.city(), "Şəhər mütləqdir."));
        branch.setDistrict(inputService.required(request.district(), "Rayon mütləqdir."));
        branch.setLatitude(request.latitude());
        branch.setLongitude(request.longitude());
        branch.setNormalizedPhone(request.phone() == null || request.phone().isBlank()
                ? null
                : phoneNumberService.normalizeAzerbaijaniPhone(request.phone()));
        branch.setNotes(inputService.optional(request.notes()));
        branch.setTimezone(inputService.timezone(request.timezone(), businessTimezone));
    }
}

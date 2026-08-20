package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IndividualWorkspaceService {
    private final IndividualWorkspaceRepository workspaceRepository;
    private final ProviderAccessService accessService;
    private final ProviderInputService inputService;
    private final ProviderWorkspaceMapper mapper;

    public IndividualWorkspaceService(
            IndividualWorkspaceRepository workspaceRepository,
            ProviderAccessService accessService,
            ProviderInputService inputService,
            ProviderWorkspaceMapper mapper
    ) {
        this.workspaceRepository = workspaceRepository;
        this.accessService = accessService;
        this.inputService = inputService;
        this.mapper = mapper;
    }

    @Transactional
    public IndividualWorkspaceResponseDto create(long userId, IndividualWorkspaceCreateRequestDto request) {
        UserEntity user = accessService.requireActiveUser(userId);
        if (workspaceRepository.existsByOwnerUserId(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "İstifadəçinin artıq şəxsi workspace-i var.");
        }
        IndividualWorkspaceEntity workspace = new IndividualWorkspaceEntity();
        workspace.setOwnerUser(user);
        workspace.setName(inputService.required(request.name(), "Workspace adı mütləqdir."));
        workspace.setTimezone(inputService.timezone(request.timezone(), "Asia/Baku"));
        workspace.setStatus(ProviderStatus.ACTIVE);
        return mapper.toDto(workspaceRepository.save(workspace));
    }

    @Transactional(readOnly = true)
    public IndividualWorkspaceResponseDto get(long workspaceId, long userId) {
        IndividualWorkspaceEntity workspace = requireOwned(workspaceId, userId);
        return mapper.toDto(workspace);
    }

    @Transactional(readOnly = true)
    public IndividualWorkspaceEntity requireOwned(long workspaceId, long userId) {
        IndividualWorkspaceEntity workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Şəxsi workspace tapılmadı."));
        validateOwned(workspace, userId);
        return workspace;
    }

    @Transactional
    public IndividualWorkspaceEntity requireOwnedForUpdate(long workspaceId, long userId) {
        IndividualWorkspaceEntity workspace = workspaceRepository.findByIdForUpdate(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Şəxsi workspace tapılmadı."));
        validateOwned(workspace, userId);
        return workspace;
    }

    private void validateOwned(IndividualWorkspaceEntity workspace, long userId) {
        if (!workspace.getOwnerUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu workspace üçün icazəniz yoxdur.");
        }
        if (workspace.getStatus() == ProviderStatus.ARCHIVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Şəxsi workspace arxivdədir.");
        }
    }
}

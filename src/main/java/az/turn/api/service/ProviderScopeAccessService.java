package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProviderScopeAccessService {
    private final ProviderAccessService providerAccessService;
    private final IndividualWorkspaceRepository workspaceRepository;

    public ProviderScopeAccessService(
            ProviderAccessService providerAccessService,
            IndividualWorkspaceRepository workspaceRepository
    ) {
        this.providerAccessService = providerAccessService;
        this.workspaceRepository = workspaceRepository;
    }

    @Transactional(readOnly = true)
    public void requireManager(ProviderScopeType scopeType, long scopeId, long userId) {
        if (scopeType == ProviderScopeType.BUSINESS) {
            providerAccessService.requireBusinessManager(scopeId, userId);
            return;
        }
        IndividualWorkspaceEntity workspace = workspaceRepository.findById(scopeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Şəxsi workspace tapılmadı."));
        if (workspace.getStatus() == ProviderStatus.ARCHIVED
                || !workspace.getOwnerUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu workspace üçün icazəniz yoxdur.");
        }
    }

    public ProviderScopeType roomScopeType(RoomEntity room) {
        return room.getBranch() == null
                ? ProviderScopeType.INDIVIDUAL_WORKSPACE
                : ProviderScopeType.BUSINESS;
    }

    public long roomScopeId(RoomEntity room) {
        return room.getBranch() == null
                ? room.getIndividualWorkspace().getId()
                : room.getBranch().getBusiness().getId();
    }
}

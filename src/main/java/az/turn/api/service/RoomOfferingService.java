package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class RoomOfferingService {
    private final RoomServiceItemRepository serviceRepository;
    private final ProviderAccessService accessService;
    private final ProviderInputService inputService;
    private final RoomConfigurationMapper mapper;

    public RoomOfferingService(
            RoomServiceItemRepository serviceRepository,
            ProviderAccessService accessService,
            ProviderInputService inputService,
            RoomConfigurationMapper mapper
    ) {
        this.serviceRepository = serviceRepository;
        this.accessService = accessService;
        this.inputService = inputService;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<RoomServiceDto> list(long roomId, long userId) {
        accessService.requireRoomViewer(roomId, userId);
        return serviceRepository.findByRoomIdOrderByCreatedAtAsc(roomId)
                .stream().map(mapper::toDto).toList();
    }

    @Transactional
    public RoomServiceDto create(long roomId, long userId, RoomServiceUpsertRequestDto request) {
        RoomEntity room = accessService.requireEditableRoom(roomId, userId);
        RoomServiceItemEntity service = new RoomServiceItemEntity();
        service.setRoom(room);
        apply(service, request);
        return mapper.toDto(serviceRepository.save(service));
    }

    @Transactional
    public RoomServiceDto update(
            long roomId,
            long serviceId,
            long userId,
            RoomServiceUpsertRequestDto request
    ) {
        accessService.requireEditableRoom(roomId, userId);
        RoomServiceItemEntity service = requireService(roomId, serviceId);
        apply(service, request);
        return mapper.toDto(serviceRepository.save(service));
    }

    @Transactional
    public void deactivate(long roomId, long serviceId, long userId) {
        accessService.requireEditableRoom(roomId, userId);
        RoomServiceItemEntity service = requireService(roomId, serviceId);
        service.setActive(false);
        serviceRepository.save(service);
    }

    private RoomServiceItemEntity requireService(long roomId, long serviceId) {
        return serviceRepository.findByIdAndRoomId(serviceId, roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Otaq xidməti tapılmadı."));
    }

    private void apply(RoomServiceItemEntity service, RoomServiceUpsertRequestDto request) {
        service.setName(inputService.required(request.name(), "Xidmət adı mütləqdir."));
        service.setDescription(inputService.optional(request.description()));
        service.setPrice(request.price());
        service.setCurrency(request.price() == null ? null : "AZN");
        service.setActive(request.active());
    }
}

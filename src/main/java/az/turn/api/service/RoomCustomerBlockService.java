package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RoomCustomerBlockService {
    private final RoomCustomerBlockRepository blockRepository;
    private final ProviderAccessService accessService;
    private final UserRepository userRepository;
    private final PlatformAuditService auditService;
    private final Clock clock;

    public RoomCustomerBlockService(
            RoomCustomerBlockRepository blockRepository,
            ProviderAccessService accessService,
            UserRepository userRepository,
            PlatformAuditService auditService,
            Clock clock
    ) {
        this.blockRepository = blockRepository;
        this.accessService = accessService;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public RoomCustomerBlockDto block(long roomId, long actorUserId, RoomCustomerBlockRequestDto request) {
        RoomEntity room = accessService.requireEditableRoom(roomId, actorUserId);
        UserEntity actor = accessService.requireActiveUser(actorUserId);
        UserEntity customer = userRepository.findById(request.customerUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Müştəri tapılmadı."));
        RoomCustomerBlockEntity block = blockRepository.findByRoomIdAndCustomerUserId(roomId, customer.getId())
                .orElseGet(RoomCustomerBlockEntity::new);
        block.setRoom(room);
        block.setCustomerUser(customer);
        block.setReason(request.reason().trim());
        block.setBlockedByUser(actor);
        block.setActive(true);
        block.setRevokedAt(null);
        block.setRevokedByUser(null);
        RoomCustomerBlockEntity saved = blockRepository.save(block);
        auditService.record(
                "USER", String.valueOf(actorUserId), "BLOCK_ROOM_CUSTOMER", "ROOM_CUSTOMER_BLOCK",
                saved.getId(), "roomId=" + roomId + ",customerUserId=" + customer.getId()
        );
        return toDto(saved);
    }

    @Transactional
    public RoomCustomerBlockDto revoke(long roomId, long customerUserId, long actorUserId) {
        RoomEntity room = accessService.requireRoomViewer(roomId, actorUserId);
        if (room.getBranch() != null) {
            accessService.requireBusinessManager(room.getBranch().getBusiness().getId(), actorUserId);
        }
        RoomCustomerBlockEntity block = blockRepository.findByRoomIdAndCustomerUserId(roomId, customerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blok qeydi tapılmadı."));
        block.setActive(false);
        block.setRevokedAt(LocalDateTime.now(clock));
        block.setRevokedByUser(accessService.requireActiveUser(actorUserId));
        RoomCustomerBlockEntity saved = blockRepository.save(block);
        auditService.record(
                "USER", String.valueOf(actorUserId), "REVOKE_ROOM_CUSTOMER_BLOCK", "ROOM_CUSTOMER_BLOCK",
                saved.getId(), "roomId=" + roomId + ",customerUserId=" + customerUserId
        );
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<RoomCustomerBlockDto> list(long roomId, long actorUserId) {
        accessService.requireRoomViewer(roomId, actorUserId);
        return blockRepository.findByRoomIdOrderByCreatedAtDesc(roomId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public void requireAllowed(long roomId, long customerUserId) {
        if (blockRepository.existsByRoomIdAndCustomerUserIdAndActiveTrue(roomId, customerUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu otaq üçün rezervasiya və növbə girişi məhdudlaşdırılıb.");
        }
    }

    private RoomCustomerBlockDto toDto(RoomCustomerBlockEntity value) {
        return new RoomCustomerBlockDto(
                value.getId(), value.getRoom().getId(), value.getCustomerUser().getId(), value.getReason(),
                value.isActive(), value.getBlockedByUser().getId(), value.getCreatedAt(), value.getRevokedAt()
        );
    }
}

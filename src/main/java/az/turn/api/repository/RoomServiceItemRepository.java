package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomServiceItemRepository extends JpaRepository<RoomServiceItemEntity, Long> {
    List<RoomServiceItemEntity> findByRoomIdOrderByCreatedAtAsc(Long roomId);
    Optional<RoomServiceItemEntity> findByIdAndRoomId(Long id, Long roomId);
}

package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomCustomerBlockRepository extends JpaRepository<RoomCustomerBlockEntity, Long> {
    boolean existsByRoomIdAndCustomerUserIdAndActiveTrue(Long roomId, Long customerUserId);
    Optional<RoomCustomerBlockEntity> findByRoomIdAndCustomerUserId(Long roomId, Long customerUserId);
    List<RoomCustomerBlockEntity> findByRoomIdOrderByCreatedAtDesc(Long roomId);
}

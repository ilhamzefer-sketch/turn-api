package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AvailabilityExceptionRepository extends JpaRepository<AvailabilityExceptionEntity, Long> {
    List<AvailabilityExceptionEntity> findByRoomIdOrderByDateAscStartTimeAsc(Long roomId);
    List<AvailabilityExceptionEntity> findByRoomIdAndDateOrderByStartTimeAsc(Long roomId, LocalDate date);
    Optional<AvailabilityExceptionEntity> findByIdAndRoomId(Long id, Long roomId);
}

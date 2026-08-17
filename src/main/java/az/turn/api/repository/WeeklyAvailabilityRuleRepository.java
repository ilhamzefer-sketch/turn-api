package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;

public interface WeeklyAvailabilityRuleRepository extends JpaRepository<WeeklyAvailabilityRuleEntity, Long> {
    List<WeeklyAvailabilityRuleEntity> findByRoomIdOrderByDayOfWeekAscStartTimeAsc(Long roomId);
    List<WeeklyAvailabilityRuleEntity> findByRoomIdAndDayOfWeekOrderByStartTimeAsc(Long roomId, DayOfWeek dayOfWeek);
    long countByRoomIdAndActiveTrue(Long roomId);
    void deleteByRoomId(Long roomId);
    void deleteByRoomIdAndDayOfWeek(Long roomId, DayOfWeek dayOfWeek);
}

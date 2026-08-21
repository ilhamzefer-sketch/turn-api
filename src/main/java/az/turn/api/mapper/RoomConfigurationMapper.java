package az.turn.api;

import org.springframework.stereotype.Component;

@Component
public class RoomConfigurationMapper {

    public WeeklyAvailabilityRuleDto toDto(WeeklyAvailabilityRuleEntity rule) {
        return new WeeklyAvailabilityRuleDto(
                rule.getId(),
                rule.getRoom().getId(),
                rule.getDayOfWeek(),
                rule.getStartTime(),
                rule.getEndTime(),
                rule.isActive()
        );
    }

    public AvailabilityExceptionDto toDto(AvailabilityExceptionEntity exception) {
        return new AvailabilityExceptionDto(
                exception.getId(),
                exception.getRoom().getId(),
                exception.getDate(),
                exception.getType(),
                exception.getStartTime(),
                exception.getEndTime(),
                exception.getReason()
        );
    }
}

package az.turn.api;

import org.springframework.stereotype.Component;

@Component
public class RoomConfigurationMapper {

    public RoomServiceDto toDto(RoomServiceItemEntity service) {
        return new RoomServiceDto(
                service.getId(),
                service.getRoom().getId(),
                service.getName(),
                service.getDescription(),
                service.getPrice(),
                service.getCurrency(),
                service.isActive(),
                service.getCreatedAt(),
                service.getUpdatedAt()
        );
    }

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

package az.turn.api;

import java.time.LocalTime;

public record AvailabilityInterval(LocalTime start, LocalTime end) {
}

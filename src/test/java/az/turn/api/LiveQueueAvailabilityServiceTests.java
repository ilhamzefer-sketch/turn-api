package az.turn.api;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiveQueueAvailabilityServiceTests {
    @Test
    void returnsNextOpeningWithRoomTimezoneOffset() {
        RoomAvailabilityService availabilityService = mock(RoomAvailabilityService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-25T04:00:00Z"), ZoneOffset.UTC);
        RoomEntity room = new RoomEntity();
        room.setTimezone("Asia/Baku");
        LocalDate roomDate = LocalDate.of(2026, 8, 25);
        when(availabilityService.intervals(room, roomDate))
                .thenReturn(List.of(new AvailabilityInterval(LocalTime.of(9, 0), LocalTime.of(18, 0))));
        LiveQueueAvailabilityService service = new LiveQueueAvailabilityService(availabilityService, clock);

        OffsetDateTime opening = service.nextOpeningAt(room);

        assertThat(opening).isEqualTo(OffsetDateTime.parse("2026-08-25T09:00:00+04:00"));
    }
}

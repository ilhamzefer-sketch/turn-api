package az.turn.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoomDefaultsTests {
    private final RoomDefaults roomDefaults = new RoomDefaults();

    @Test
    void usesPublicVisibilityWhenVisibilityIsNotProvided() {
        assertThat(roomDefaults.visibility(null)).isEqualTo(RoomVisibility.PUBLIC);
    }

    @Test
    void preservesAnExplicitVisibilityChoice() {
        assertThat(roomDefaults.visibility(RoomVisibility.UNLISTED)).isEqualTo(RoomVisibility.UNLISTED);
        assertThat(roomDefaults.visibility(RoomVisibility.PRIVATE)).isEqualTo(RoomVisibility.PRIVATE);
    }
}

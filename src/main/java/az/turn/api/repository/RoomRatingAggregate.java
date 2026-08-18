package az.turn.api;

public interface RoomRatingAggregate {
    Long getRoomId();
    Double getAverageScore();
    Long getRatingCount();
}

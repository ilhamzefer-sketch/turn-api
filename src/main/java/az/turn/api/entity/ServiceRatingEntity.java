package az.turn.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "service_ratings")
public class ServiceRatingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "live_queue_entry_id", unique = true)
    private LiveQueueEntryEntity liveQueueEntry;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "planned_booking_id", unique = true)
    private PlannedBookingEntity plannedBooking;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_user_id", nullable = false)
    private UserEntity customerUser;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private RoomEntity room;
    @Column(nullable = false)
    private int score;
    @Column(length = 1000)
    private String comment;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    @Column(nullable = false)
    private LocalDateTime editableUntil;

    public Long getId() { return id; }
    public LiveQueueEntryEntity getLiveQueueEntry() { return liveQueueEntry; }
    public void setLiveQueueEntry(LiveQueueEntryEntity value) { this.liveQueueEntry = value; }
    public PlannedBookingEntity getPlannedBooking() { return plannedBooking; }
    public void setPlannedBooking(PlannedBookingEntity value) { this.plannedBooking = value; }
    public UserEntity getCustomerUser() { return customerUser; }
    public void setCustomerUser(UserEntity value) { this.customerUser = value; }
    public RoomEntity getRoom() { return room; }
    public void setRoom(RoomEntity value) { this.room = value; }
    public int getScore() { return score; }
    public void setScore(int value) { this.score = value; }
    public String getComment() { return comment; }
    public void setComment(String value) { this.comment = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getEditableUntil() { return editableUntil; }
    public void setEditableUntil(LocalDateTime value) { this.editableUntil = value; }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() { updatedAt = LocalDateTime.now(); }
}

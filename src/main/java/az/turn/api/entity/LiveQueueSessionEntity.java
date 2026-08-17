package az.turn.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "live_queue_sessions")
public class LiveQueueSessionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private RoomEntity room;
    @Column(nullable = false)
    private LocalDate serviceDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LiveQueueSessionStatus status;
    @Column
    private Integer openSlot;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LiveQueueAcceptanceOverride acceptanceOverride;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LiveQueueResetPolicy resetPolicy;
    @Column
    private LocalTime resetLocalTime;
    @Column
    private Integer resetIntervalMinutes;
    @Column(nullable = false)
    private LocalDateTime nextResetAt;
    @Column(nullable = false)
    private long nextPosition;
    @Column(nullable = false)
    private LocalDateTime openedAt;
    @Column
    private LocalDateTime closedAt;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public RoomEntity getRoom() { return room; }
    public void setRoom(RoomEntity value) { this.room = value; }
    public LocalDate getServiceDate() { return serviceDate; }
    public void setServiceDate(LocalDate value) { this.serviceDate = value; }
    public LiveQueueSessionStatus getStatus() { return status; }
    public void setStatus(LiveQueueSessionStatus value) { this.status = value; }
    public Integer getOpenSlot() { return openSlot; }
    public void setOpenSlot(Integer value) { this.openSlot = value; }
    public LiveQueueAcceptanceOverride getAcceptanceOverride() { return acceptanceOverride; }
    public void setAcceptanceOverride(LiveQueueAcceptanceOverride value) { this.acceptanceOverride = value; }
    public LiveQueueResetPolicy getResetPolicy() { return resetPolicy; }
    public void setResetPolicy(LiveQueueResetPolicy value) { this.resetPolicy = value; }
    public LocalTime getResetLocalTime() { return resetLocalTime; }
    public void setResetLocalTime(LocalTime value) { this.resetLocalTime = value; }
    public Integer getResetIntervalMinutes() { return resetIntervalMinutes; }
    public void setResetIntervalMinutes(Integer value) { this.resetIntervalMinutes = value; }
    public LocalDateTime getNextResetAt() { return nextResetAt; }
    public void setNextResetAt(LocalDateTime value) { this.nextResetAt = value; }
    public long getNextPosition() { return nextPosition; }
    public void setNextPosition(long value) { this.nextPosition = value; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(LocalDateTime value) { this.openedAt = value; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime value) { this.closedAt = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { this.updatedAt = value; }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() { updatedAt = LocalDateTime.now(); }
}

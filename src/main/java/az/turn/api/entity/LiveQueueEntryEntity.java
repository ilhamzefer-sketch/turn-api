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

import java.time.LocalDateTime;

@Entity
@Table(name = "live_queue_entries")
public class LiveQueueEntryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private LiveQueueSessionEntity session;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private RoomEntity room;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_contact_id")
    private GuestContactEntity guestContact;
    @Column(nullable = false)
    private long queuePosition;
    @Column(nullable = false, unique = true, length = 24)
    private String publicReference;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LiveQueueEntryStatus status;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LiveQueueEntrySource source;
    @Column(length = 20)
    private String activeIdentityKey;
    @Column
    private Integer currentSlot;
    @Column(length = 160)
    private String privateDisplayName;
    @Column(length = 1000)
    private String internalNote;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private UserEntity createdByUser;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    @Column
    private LocalDateTime completedAt;
    @Column
    private LocalDateTime removedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LiveQueueSessionEntity getSession() { return session; }
    public void setSession(LiveQueueSessionEntity value) { this.session = value; }
    public RoomEntity getRoom() { return room; }
    public void setRoom(RoomEntity value) { this.room = value; }
    public UserEntity getUser() { return user; }
    public void setUser(UserEntity value) { this.user = value; }
    public GuestContactEntity getGuestContact() { return guestContact; }
    public void setGuestContact(GuestContactEntity value) { this.guestContact = value; }
    public long getQueuePosition() { return queuePosition; }
    public void setQueuePosition(long value) { this.queuePosition = value; }
    public String getPublicReference() { return publicReference; }
    public void setPublicReference(String value) { this.publicReference = value; }
    public LiveQueueEntryStatus getStatus() { return status; }
    public void setStatus(LiveQueueEntryStatus value) { this.status = value; }
    public LiveQueueEntrySource getSource() { return source; }
    public void setSource(LiveQueueEntrySource value) { this.source = value; }
    public String getActiveIdentityKey() { return activeIdentityKey; }
    public void setActiveIdentityKey(String value) { this.activeIdentityKey = value; }
    public Integer getCurrentSlot() { return currentSlot; }
    public void setCurrentSlot(Integer value) { this.currentSlot = value; }
    public String getPrivateDisplayName() { return privateDisplayName; }
    public void setPrivateDisplayName(String value) { this.privateDisplayName = value; }
    public String getInternalNote() { return internalNote; }
    public void setInternalNote(String value) { this.internalNote = value; }
    public UserEntity getCreatedByUser() { return createdByUser; }
    public void setCreatedByUser(UserEntity value) { this.createdByUser = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { this.updatedAt = value; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime value) { this.completedAt = value; }
    public LocalDateTime getRemovedAt() { return removedAt; }
    public void setRemovedAt(LocalDateTime value) { this.removedAt = value; }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() { updatedAt = LocalDateTime.now(); }
}

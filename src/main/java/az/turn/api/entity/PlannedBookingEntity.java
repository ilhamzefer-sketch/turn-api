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
@Table(name = "planned_bookings")
public class PlannedBookingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private RoomEntity room;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_contact_id")
    private GuestContactEntity guestContact;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_service_id")
    private RoomServiceItemEntity roomService;
    @Column(nullable = false, unique = true, length = 24)
    private String bookingReference;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PlannedBookingStatus status;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LiveQueueEntrySource source;
    @Column(nullable = false)
    private LocalDateTime startAt;
    @Column(nullable = false)
    private LocalDateTime endAt;
    @Column(nullable = false)
    private LocalDateTime blockingEndAt;
    @Column
    private Integer activeSlot;
    @Column
    private Integer activeCustomerSlot;
    @Column(length = 1000)
    private String customerNote;
    @Column(length = 1000)
    private String internalNote;
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private BookingCancellationReason cancellationReason;
    @Column(length = 500)
    private String cancellationDetail;
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
    private LocalDateTime cancelledAt;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public RoomEntity getRoom() { return room; }
    public void setRoom(RoomEntity value) { this.room = value; }
    public UserEntity getUser() { return user; }
    public void setUser(UserEntity value) { this.user = value; }
    public GuestContactEntity getGuestContact() { return guestContact; }
    public void setGuestContact(GuestContactEntity value) { this.guestContact = value; }
    public RoomServiceItemEntity getRoomService() { return roomService; }
    public void setRoomService(RoomServiceItemEntity value) { this.roomService = value; }
    public String getBookingReference() { return bookingReference; }
    public void setBookingReference(String value) { this.bookingReference = value; }
    public PlannedBookingStatus getStatus() { return status; }
    public void setStatus(PlannedBookingStatus value) { this.status = value; }
    public LiveQueueEntrySource getSource() { return source; }
    public void setSource(LiveQueueEntrySource value) { this.source = value; }
    public LocalDateTime getStartAt() { return startAt; }
    public void setStartAt(LocalDateTime value) { this.startAt = value; }
    public LocalDateTime getEndAt() { return endAt; }
    public void setEndAt(LocalDateTime value) { this.endAt = value; }
    public LocalDateTime getBlockingEndAt() { return blockingEndAt; }
    public void setBlockingEndAt(LocalDateTime value) { this.blockingEndAt = value; }
    public Integer getActiveSlot() { return activeSlot; }
    public void setActiveSlot(Integer value) { this.activeSlot = value; }
    public Integer getActiveCustomerSlot() { return activeCustomerSlot; }
    public void setActiveCustomerSlot(Integer value) { this.activeCustomerSlot = value; }
    public String getCustomerNote() { return customerNote; }
    public void setCustomerNote(String value) { this.customerNote = value; }
    public String getInternalNote() { return internalNote; }
    public void setInternalNote(String value) { this.internalNote = value; }
    public BookingCancellationReason getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(BookingCancellationReason value) { this.cancellationReason = value; }
    public String getCancellationDetail() { return cancellationDetail; }
    public void setCancellationDetail(String value) { this.cancellationDetail = value; }
    public UserEntity getCreatedByUser() { return createdByUser; }
    public void setCreatedByUser(UserEntity value) { this.createdByUser = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { this.updatedAt = value; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime value) { this.completedAt = value; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime value) { this.cancelledAt = value; }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() { updatedAt = LocalDateTime.now(); }
}

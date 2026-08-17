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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "rooms")
public class RoomEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private BranchEntity branch;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "individual_workspace_id", unique = true)
    private IndividualWorkspaceEntity individualWorkspace;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private UserEntity createdByUser;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(length = 80)
    private String roomNumberOrCode;
    @Column(length = 2000)
    private String description;
    @Column(length = 2000)
    private String notes;
    @Column(nullable = false, length = 60)
    private String timezone;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReservationMode reservationMode;
    @Column(nullable = false)
    private int defaultSlotDurationMinutes;
    @Column(nullable = false)
    private int appointmentBufferMinutes;
    @Column(nullable = false)
    private int bookingWindowDays;
    @Column(nullable = false)
    private int minimumAdvanceMinutes;
    @Column(nullable = false)
    private int cancellationCutoffMinutes;
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private LiveQueueResetPolicy liveQueueResetPolicy;
    @Column
    private LocalTime liveQueueResetLocalTime;
    @Column
    private Integer liveQueueResetIntervalMinutes;
    @Column
    private Integer liveQueueMaxParticipants;
    @Column(nullable = false)
    private boolean liveQueueAcceptingNewEntries;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RoomStatus status;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RoomVisibility visibility;
    @Column(length = 500)
    private String personalPublicAddress;
    @Column(precision = 9, scale = 6)
    private BigDecimal personalLatitude;
    @Column(precision = 9, scale = 6)
    private BigDecimal personalLongitude;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    @Column
    private LocalDateTime archivedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public BranchEntity getBranch() { return branch; }
    public void setBranch(BranchEntity branch) { this.branch = branch; }
    public IndividualWorkspaceEntity getIndividualWorkspace() { return individualWorkspace; }
    public void setIndividualWorkspace(IndividualWorkspaceEntity value) { this.individualWorkspace = value; }
    public UserEntity getCreatedByUser() { return createdByUser; }
    public void setCreatedByUser(UserEntity value) { this.createdByUser = value; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRoomNumberOrCode() { return roomNumberOrCode; }
    public void setRoomNumberOrCode(String value) { this.roomNumberOrCode = value; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public ReservationMode getReservationMode() { return reservationMode; }
    public void setReservationMode(ReservationMode value) { this.reservationMode = value; }
    public int getDefaultSlotDurationMinutes() { return defaultSlotDurationMinutes; }
    public void setDefaultSlotDurationMinutes(int value) { this.defaultSlotDurationMinutes = value; }
    public int getAppointmentBufferMinutes() { return appointmentBufferMinutes; }
    public void setAppointmentBufferMinutes(int value) { this.appointmentBufferMinutes = value; }
    public int getBookingWindowDays() { return bookingWindowDays; }
    public void setBookingWindowDays(int value) { this.bookingWindowDays = value; }
    public int getMinimumAdvanceMinutes() { return minimumAdvanceMinutes; }
    public void setMinimumAdvanceMinutes(int value) { this.minimumAdvanceMinutes = value; }
    public int getCancellationCutoffMinutes() { return cancellationCutoffMinutes; }
    public void setCancellationCutoffMinutes(int value) { this.cancellationCutoffMinutes = value; }
    public LiveQueueResetPolicy getLiveQueueResetPolicy() { return liveQueueResetPolicy; }
    public void setLiveQueueResetPolicy(LiveQueueResetPolicy value) { this.liveQueueResetPolicy = value; }
    public LocalTime getLiveQueueResetLocalTime() { return liveQueueResetLocalTime; }
    public void setLiveQueueResetLocalTime(LocalTime value) { this.liveQueueResetLocalTime = value; }
    public Integer getLiveQueueResetIntervalMinutes() { return liveQueueResetIntervalMinutes; }
    public void setLiveQueueResetIntervalMinutes(Integer value) { this.liveQueueResetIntervalMinutes = value; }
    public Integer getLiveQueueMaxParticipants() { return liveQueueMaxParticipants; }
    public void setLiveQueueMaxParticipants(Integer value) { this.liveQueueMaxParticipants = value; }
    public boolean isLiveQueueAcceptingNewEntries() { return liveQueueAcceptingNewEntries; }
    public void setLiveQueueAcceptingNewEntries(boolean value) { this.liveQueueAcceptingNewEntries = value; }
    public RoomStatus getStatus() { return status; }
    public void setStatus(RoomStatus status) { this.status = status; }
    public RoomVisibility getVisibility() { return visibility; }
    public void setVisibility(RoomVisibility visibility) { this.visibility = visibility; }
    public String getPersonalPublicAddress() { return personalPublicAddress; }
    public void setPersonalPublicAddress(String value) { this.personalPublicAddress = value; }
    public BigDecimal getPersonalLatitude() { return personalLatitude; }
    public void setPersonalLatitude(BigDecimal value) { this.personalLatitude = value; }
    public BigDecimal getPersonalLongitude() { return personalLongitude; }
    public void setPersonalLongitude(BigDecimal value) { this.personalLongitude = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(LocalDateTime archivedAt) { this.archivedAt = archivedAt; }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (bookingWindowDays == 0) bookingWindowDays = 30;
        if (minimumAdvanceMinutes == 0) minimumAdvanceMinutes = 30;
        liveQueueAcceptingNewEntries = true;
    }

    @PreUpdate
    public void preUpdate() { updatedAt = LocalDateTime.now(); }
}

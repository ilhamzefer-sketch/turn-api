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
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "booking_audit_events")
public class BookingAuditEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private PlannedBookingEntity booking;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BookingAuditAction action;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_user_id", nullable = false)
    private UserEntity actorUser;
    @Column
    private LocalDateTime oldStartAt;
    @Column
    private LocalDateTime oldEndAt;
    @Column
    private LocalDateTime newStartAt;
    @Column
    private LocalDateTime newEndAt;
    @Column(length = 500)
    private String reason;
    @Column(nullable = false)
    private boolean participantInformed;
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public PlannedBookingEntity getBooking() { return booking; }
    public void setBooking(PlannedBookingEntity value) { this.booking = value; }
    public BookingAuditAction getAction() { return action; }
    public void setAction(BookingAuditAction value) { this.action = value; }
    public UserEntity getActorUser() { return actorUser; }
    public void setActorUser(UserEntity value) { this.actorUser = value; }
    public LocalDateTime getOldStartAt() { return oldStartAt; }
    public void setOldStartAt(LocalDateTime value) { this.oldStartAt = value; }
    public LocalDateTime getOldEndAt() { return oldEndAt; }
    public void setOldEndAt(LocalDateTime value) { this.oldEndAt = value; }
    public LocalDateTime getNewStartAt() { return newStartAt; }
    public void setNewStartAt(LocalDateTime value) { this.newStartAt = value; }
    public LocalDateTime getNewEndAt() { return newEndAt; }
    public void setNewEndAt(LocalDateTime value) { this.newEndAt = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { this.reason = value; }
    public boolean isParticipantInformed() { return participantInformed; }
    public void setParticipantInformed(boolean value) { this.participantInformed = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}

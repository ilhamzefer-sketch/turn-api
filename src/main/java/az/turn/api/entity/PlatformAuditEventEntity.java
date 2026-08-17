package az.turn.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "platform_audit_events")
public class PlatformAuditEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 30)
    private String actorType;
    @Column(nullable = false, length = 120)
    private String actorReference;
    @Column(nullable = false, length = 100)
    private String action;
    @Column(nullable = false, length = 60)
    private String targetType;
    @Column
    private Long targetId;
    @Column(length = 3000)
    private String details;
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public String getActorType() { return actorType; }
    public void setActorType(String value) { this.actorType = value; }
    public String getActorReference() { return actorReference; }
    public void setActorReference(String value) { this.actorReference = value; }
    public String getAction() { return action; }
    public void setAction(String value) { this.action = value; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String value) { this.targetType = value; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long value) { this.targetId = value; }
    public String getDetails() { return details; }
    public void setDetails(String value) { this.details = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }

    @PrePersist
    public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}

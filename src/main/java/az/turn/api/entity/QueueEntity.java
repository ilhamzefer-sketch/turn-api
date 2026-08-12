package az.turn.api;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "queues")
public class QueueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_id", nullable = false)
    private RegistrationEntity registration;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String serviceName;

    @ElementCollection
    @CollectionTable(name = "queue_categories", joinColumns = @JoinColumn(name = "queue_id"))
    @Column(name = "category", nullable = false)
    private List<String> categories = new ArrayList<>();

    @Column(nullable = false, unique = true)
    private String qrToken;

    @Column(name = "current_queue_number", nullable = false)
    private long legacyCurrentQueueNumber;

    @Column(nullable = false)
    private long currentServingNumber;

    @Column(nullable = false)
    private long lastIssuedNumber;

    @Column(nullable = false)
    private long averageServiceMinutes;

    @Column(nullable = false)
    private long servedCustomersCount;

    @Column(nullable = false)
    private long totalServiceMinutes;

    @Column
    private LocalDateTime lastAdvancedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QueueResetMode resetMode;

    @Column
    private LocalDateTime resetAt;

    @Column(nullable = false)
    private boolean active;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public RegistrationEntity getRegistration() {
        return registration;
    }

    public void setRegistration(RegistrationEntity registration) {
        this.registration = registration;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

    public String getQrToken() {
        return qrToken;
    }

    public void setQrToken(String qrToken) {
        this.qrToken = qrToken;
    }

    public long getCurrentServingNumber() {
        return currentServingNumber;
    }

    public void setCurrentServingNumber(long currentServingNumber) {
        this.currentServingNumber = currentServingNumber;
        this.legacyCurrentQueueNumber = currentServingNumber;
    }

    public long getLastIssuedNumber() {
        return lastIssuedNumber;
    }

    public void setLastIssuedNumber(long lastIssuedNumber) {
        this.lastIssuedNumber = lastIssuedNumber;
    }

    public long getAverageServiceMinutes() {
        return averageServiceMinutes;
    }

    public void setAverageServiceMinutes(long averageServiceMinutes) {
        this.averageServiceMinutes = averageServiceMinutes;
    }

    public long getServedCustomersCount() {
        return servedCustomersCount;
    }

    public void setServedCustomersCount(long servedCustomersCount) {
        this.servedCustomersCount = servedCustomersCount;
    }

    public long getTotalServiceMinutes() {
        return totalServiceMinutes;
    }

    public void setTotalServiceMinutes(long totalServiceMinutes) {
        this.totalServiceMinutes = totalServiceMinutes;
    }

    public LocalDateTime getLastAdvancedAt() {
        return lastAdvancedAt;
    }

    public void setLastAdvancedAt(LocalDateTime lastAdvancedAt) {
        this.lastAdvancedAt = lastAdvancedAt;
    }

    public QueueResetMode getResetMode() {
        return resetMode;
    }

    public void setResetMode(QueueResetMode resetMode) {
        this.resetMode = resetMode;
    }

    public LocalDateTime getResetAt() {
        return resetAt;
    }

    public void setResetAt(LocalDateTime resetAt) {
        this.resetAt = resetAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public long getLegacyCurrentQueueNumber() {
        return legacyCurrentQueueNumber;
    }

    public void setLegacyCurrentQueueNumber(long legacyCurrentQueueNumber) {
        this.legacyCurrentQueueNumber = legacyCurrentQueueNumber;
    }
}

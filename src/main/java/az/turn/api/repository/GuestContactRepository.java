package az.turn.api;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface GuestContactRepository extends JpaRepository<GuestContactEntity, Long> {
    Optional<GuestContactEntity> findByNormalizedPhone(String normalizedPhone);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select contact from GuestContactEntity contact where contact.normalizedPhone = :normalizedPhone")
    Optional<GuestContactEntity> findByNormalizedPhoneForUpdate(String normalizedPhone);
}

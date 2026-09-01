package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SecureAttachmentRepository extends JpaRepository<SecureAttachmentEntity, Long> {
    Optional<SecureAttachmentEntity> findByIdAndOwnerUserId(long attachmentId, long ownerUserId);
}

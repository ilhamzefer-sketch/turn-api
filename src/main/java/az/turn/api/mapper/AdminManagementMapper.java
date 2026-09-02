package az.turn.api;

import org.springframework.stereotype.Component;

@Component
public class AdminManagementMapper {
    public AdminAccountDto toAdminAccountDto(AdminAccountEntity entity) {
        return new AdminAccountDto(
                entity.getId(),
                entity.getUsername(),
                entity.getDisplayName(),
                entity.isActive(),
                entity.getCreatedByUsername(),
                entity.getCreatedAt()
        );
    }

    public AdminUserDto toAdminUserDto(UserEntity user, long balance) {
        return new AdminUserDto(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getNormalizedPhone(),
                user.getStatus(),
                balance,
                user.getConfirmedWalletFraudCount(),
                user.getCreatedAt()
        );
    }

    public AdminBusinessDto toAdminBusinessDto(
            BusinessEntity business,
            long roomCount,
            ProviderSubscriptionEntity subscription
    ) {
        UserEntity owner = business.getPrimaryOwnerUser();
        return new AdminBusinessDto(
                business.getId(),
                business.getName(),
                business.getStatus(),
                owner.getId(),
                owner.getFirstName() + " " + owner.getLastName(),
                owner.getNormalizedPhone(),
                roomCount,
                subscription == null ? null : subscription.getRoomLimit(),
                subscription == null ? null : subscription.getStatus()
        );
    }
}

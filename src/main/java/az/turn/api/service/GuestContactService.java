package az.turn.api;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class GuestContactService {
    private final GuestContactRepository guestContactRepository;
    private final UserRepository userRepository;
    private final PhoneNumberService phoneNumberService;
    private final Clock clock;

    public GuestContactService(
            GuestContactRepository guestContactRepository,
            UserRepository userRepository,
            PhoneNumberService phoneNumberService,
            Clock clock
    ) {
        this.guestContactRepository = guestContactRepository;
        this.userRepository = userRepository;
        this.phoneNumberService = phoneNumberService;
        this.clock = clock;
    }

    public GuestContactEntity resolve(String displayName, String phone) {
        String normalizedPhone = phoneNumberService.normalizeAzerbaijaniPhone(phone);
        GuestContactEntity contact = guestContactRepository.findByNormalizedPhoneForUpdate(normalizedPhone).orElse(null);
        if (contact == null) {
            contact = new GuestContactEntity();
            contact.setNormalizedPhone(normalizedPhone);
        }
        contact.setDisplayName(displayName.trim());
        if (contact.getLinkedUser() == null) {
            UserEntity user = userRepository.findByNormalizedPhone(normalizedPhone)
                    .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                    .orElse(null);
            if (user != null) {
                contact.setLinkedUser(user);
                contact.setLinkedAt(LocalDateTime.now(clock));
            }
        }
        return guestContactRepository.save(contact);
    }

    public String identityKey(String normalizedPhone) {
        return "P:" + normalizedPhone;
    }
}
